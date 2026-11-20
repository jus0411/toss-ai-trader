package com.toss.trading.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toss.trading.domain.AssetHistory;
import com.toss.trading.domain.OrderRecord;
import com.toss.trading.domain.TradingBot;
import com.toss.trading.repository.AssetHistoryRepository;
import com.toss.trading.repository.OrderRecordRepository;
import com.toss.trading.repository.TradingBotRepository;
import com.toss.trading.strategy.TradingStrategy;
import com.toss.trading.toss.TossApiClient;
import com.toss.trading.toss.TossOrderService;
import com.toss.trading.toss.dto.TossDto;
import com.toss.trading.notification.NotificationService;
import com.toss.trading.risk.AccountKillSwitchService;
import com.toss.trading.event.EventDrivenTradingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class TradingScheduler {

    private final TradingBotRepository botRepository;
    private final OrderRecordRepository orderRepository;
    private final AssetHistoryRepository assetHistoryRepository;
    private final TossApiClient apiClient;
    private final TossOrderService orderService;
    private final NotificationService notificationService;
    private final Map<String, TradingStrategy> strategyMap;
    private final ObjectMapper objectMapper;
    private final String tradingMode;
    private final AccountKillSwitchService killSwitchService;
    private final EventDrivenTradingService eventDrivenTradingService;
    private LocalDateTime lastRebalanceTime;

    private final com.toss.trading.email.EmailService emailService;
    private final com.toss.trading.risk.CircuitBreakerService circuitBreakerService;

    public TradingScheduler(
            TradingBotRepository botRepository,
            OrderRecordRepository orderRepository,
            AssetHistoryRepository assetHistoryRepository,
            TossApiClient apiClient,
            TossOrderService orderService,
            NotificationService notificationService,
            List<TradingStrategy> strategies,
            ObjectMapper objectMapper,
            @Value("${trading.mode}") String tradingMode,
            AccountKillSwitchService killSwitchService,
            EventDrivenTradingService eventDrivenTradingService,
            com.toss.trading.email.EmailService emailService,
            com.toss.trading.risk.CircuitBreakerService circuitBreakerService) {
        this.botRepository = botRepository;
        this.orderRepository = orderRepository;
        this.assetHistoryRepository = assetHistoryRepository;
        this.apiClient = apiClient;
        this.orderService = orderService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.tradingMode = tradingMode;
        this.killSwitchService = killSwitchService;
        this.eventDrivenTradingService = eventDrivenTradingService;
        this.emailService = emailService;
        this.circuitBreakerService = circuitBreakerService;

        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(TradingStrategy::getStrategyName, s -> s));
    }

    /**
     * 매 30초마다 활성화된 봇들의 매매 조건 검증 및 실시간 감시 상태 갱신
     */
    @Scheduled(fixedDelay = 30000)
    public void runAutoTradingLoop() {
        // 장 개설 여부 판단 (실전 모드일 때만 적용, 가상 모드에서는 24시간 작동 허용)
        if ("real".equalsIgnoreCase(tradingMode) && !isKoreanMarketOpen()) {
            return;
        }

        // 0. 계좌 마스터 킬 스위치 (낙폭 감시 및 긴급 셧다운)
        boolean shutdownTriggered = killSwitchService.checkAndTriggerShutdown();
        if (shutdownTriggered) {
            return;
        }

        // 0-1. 공시 및 호재 뉴스 기습 크롤링 추격 매수 감시
        try {
            eventDrivenTradingService.scanAndTriggerEventTrading();
        } catch (Exception e) {
            System.err.println("Event-driven scan failed: " + e.getMessage());
        }

        List<TradingBot> activeBots = botRepository.findByActive(true);
        if (activeBots.isEmpty()) {
            return;
        }

        System.out.println("Running auto trading scan: " + LocalDateTime.now() + " (Bots count: " + activeBots.size() + ")");

        // 1. 감시 종목 시세 수집 (가상 PORTFOLIO 봇의 실제 타겟 종목들도 수집에 반영)
        java.util.Set<String> allSymbols = new java.util.HashSet<>();
        TradingBot portfolioBot = null;

        for (TradingBot bot : activeBots) {
            if ("PORTFOLIO".equalsIgnoreCase(bot.getStockCode())) {
                portfolioBot = bot;
                try {
                    Map<String, Double> targets = objectMapper.readValue(bot.getParameters(), new TypeReference<>() {});
                    allSymbols.addAll(targets.keySet());
                } catch (Exception e) {
                    System.err.println("Failed to parse portfolio targets: " + e.getMessage());
                }
            } else {
                allSymbols.add(bot.getStockCode());
            }
        }

        List<String> symbols = allSymbols.stream().toList();
        if (symbols.isEmpty()) {
            return;
        }

        List<TossDto.PriceDto> prices = apiClient.getPrices(symbols);
        Map<String, BigDecimal> currentPrices = prices.stream()
                .collect(Collectors.toMap(TossDto.PriceDto::getSymbol, TossDto.PriceDto::getLastPrice));

        // 2. 가상 모드(Mock)일 때, 현재 보유 주식 평가액과 자산을 재계산하여 DB 업데이트
        if ("mock".equalsIgnoreCase(tradingMode)) {
            recalculateMockAssets(currentPrices);
        }

        // 이 봇들의 전체 거래 이력 로드 (뉴스/상태메세지 연산에 필요)
        List<OrderRecord> orderHistory = orderRepository.findAllByOrderByOrderTimeDesc();

        // 3. 포트폴리오 리밸런싱 실행 (5분 주기로 감시하여 비중 조절)
        if (portfolioBot != null) {
            portfolioBot.setStatusMessage("포트폴리오 비중 리밸런싱 정상 가동 중 (5분 주기 감시)");
            botRepository.save(portfolioBot);
            if (lastRebalanceTime == null || lastRebalanceTime.isBefore(LocalDateTime.now().minusMinutes(5))) {
                executePortfolioRebalancing(portfolioBot, currentPrices);
                lastRebalanceTime = LocalDateTime.now();
            }
        }

        // 4. 일반 종목별 매매 조건 평가 및 실행 (PORTFOLIO 가상 코드는 스킵)
        for (TradingBot bot : activeBots) {
            if ("PORTFOLIO".equalsIgnoreCase(bot.getStockCode())) {
                continue; // 리밸런싱 봇은 위에서 일괄 처리되므로 스킵
            }

            BigDecimal currentPrice = currentPrices.get(bot.getStockCode());
            if (currentPrice == null) {
                continue;
            }

            // [Circuit Breaker] 긴급 급락 감시 및 강제 셧다운
            boolean cbTriggered = circuitBreakerService.checkAndTrigger(bot, currentPrice);
            if (cbTriggered) {
                System.out.println("[CircuitBreaker] Triggered and deactivated bot for: " + bot.getStockCode());
                continue;
            }

            // 봇 실시간 감시 상태 피드백 갱신 및 DB 저장
            String statusMsg = generateStatusMessage(bot, currentPrice, orderHistory, apiClient);
            bot.setStatusMessage(statusMsg);
            botRepository.save(bot);

            TradingStrategy strategy = strategyMap.get(bot.getAlgorithmType());
            if (strategy == null) {
                System.err.println("Unknown strategy type: " + bot.getAlgorithmType() + " for bot " + bot.getStockCode());
                continue;
            }

            // 실시간 지능형 손절/익절 (Risk Control) 판단 및 감시 실행
            boolean riskTriggered = checkAndExecuteRiskControl(bot, currentPrice, orderHistory);
            if (riskTriggered) {
                continue; // 손절/익절 처리되었으므로 일반 전략 평가는 건너뜀
            }

            // 매수 조건 검증
            if (strategy.shouldBuy(bot, currentPrice, orderHistory, apiClient)) {
                try {
                    Map<String, Object> params = objectMapper.readValue(bot.getParameters(), new TypeReference<>() {});
                    BigDecimal amount;
                    if (params.containsKey("quantity")) {
                        double qty = ((Number) params.get("quantity")).doubleValue();
                        amount = BigDecimal.valueOf(qty * currentPrice.doubleValue());
                    } else {
                        amount = BigDecimal.valueOf(((Number) params.getOrDefault("amount", 10000)).doubleValue());
                    }

                    System.out.printf("[Signal] BUY Triggered for %s (%s) at price %s%n", bot.getStockName(), bot.getStockCode(), currentPrice);
                    OrderRecord filledOrder = orderService.executeBuy(bot, currentPrice, amount);
                    if (filledOrder != null) {
                        notificationService.sendOrderAlert(filledOrder);
                    }
                } catch (Exception e) {
                    System.err.println("Buy execution error for " + bot.getStockCode() + ": " + e.getMessage());
                }
            } 
            // 매도 조건 검증
            else if (strategy.shouldSell(bot, currentPrice, orderHistory, apiClient)) {
                try {
                    System.out.printf("[Signal] SELL Triggered for %s (%s) at price %s%n", bot.getStockName(), bot.getStockCode(), currentPrice);
                    OrderRecord filledOrder = orderService.executeSell(bot, currentPrice);
                    if (filledOrder != null) {
                        notificationService.sendOrderAlert(filledOrder);
                    }
                } catch (Exception e) {
                    System.err.println("Sell execution error for " + bot.getStockCode() + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * 매일 오후 4시(장 마감 후)에 일일 포트폴리오 자산 스냅샷 기록 및 이메일 리포트 발송
     */
    @Scheduled(cron = "0 0 16 * * MON-FRI")
    public void runDailyReportAndLog() {
        System.out.println("Executing Daily Asset Report Snapshot and Email Alert...");
        
        // 실전 모드인 경우 토스 OpenAPI를 조회하여 현재 자산 상태 업데이트
        if ("real".equalsIgnoreCase(tradingMode)) {
            try {
                TossDto.HoldingsOverviewDto holdings = apiClient.getHoldings();
                // 여기에 실계좌 예수금 조회 로직을 포함시켜 자산 상태를 저장할 수 있습니다.
                // 여기서는 예시로 보유 주식 평가액과 매수가능금액(예수금)을 활용해 DB에 저장
                BigDecimal stockVal = holdings != null && holdings.getMarketValue() != null ? holdings.getMarketValue().getAmount() : BigDecimal.ZERO;
                
                // 매일 장 마감 후 최종 AssetHistory 기록
                AssetHistory snapshot = new AssetHistory();
                snapshot.setRecordDate(LocalDate.now());
                snapshot.setStockEvaluation(stockVal.doubleValue());
                // 예수금은 가상 또는 토스 잔고 조회 (매수가능 금액 조회 API `/api/v1/buying-power` 등)로 채움.
                // 편의상 직전 기록이 있다면 그것을 사용하고 없으면 0
                List<AssetHistory> history = assetHistoryRepository.findAllByOrderByRecordDateAsc();
                double lastCash = history.isEmpty() ? 0.0 : history.get(history.size() - 1).getCashBalance();
                snapshot.setCashBalance(lastCash);
                snapshot.setTotalAsset(lastCash + stockVal.doubleValue());
                assetHistoryRepository.save(snapshot);
            } catch (Exception e) {
                System.err.println("Failed to log real assets snapshot: " + e.getMessage());
            }
        }

        // 일일 리포트 이메일 발송
        notificationService.sendDailyReport();
    }

    /**
     * 가상 투자(Mock) 환경에서 현재 가격 변동을 반영해 실시간 주식 평가금과 자산을 정산합니다.
     */
    private void recalculateMockAssets(Map<String, BigDecimal> currentPrices) {
        AssetHistory currentAsset = assetHistoryRepository.findFirstByOrderByRecordTimeDesc().orElse(null);
        if (currentAsset == null) {
            currentAsset = new AssetHistory();
            currentAsset.setRecordDate(LocalDate.now());
            currentAsset.setRecordTime(LocalDateTime.now());
            currentAsset.setCashBalance(10_000_000.0);
            currentAsset.setStockEvaluation(0.0);
            currentAsset.setTotalAsset(10_000_000.0);
            currentAsset = assetHistoryRepository.save(currentAsset);
        }

        // 현재 체결 기록을 기준으로 보유한 각 주식의 수량을 구함
        List<OrderRecord> orders = orderRepository.findAllByOrderByOrderTimeDesc();
        Map<String, BigDecimal> holdingQuantities = new HashMap<>();

        for (OrderRecord order : orders) {
            if (!"FILLED".equalsIgnoreCase(order.getStatus())) continue;
            BigDecimal qty = BigDecimal.valueOf(order.getQuantity());
            if ("BUY".equalsIgnoreCase(order.getOrderType())) {
                holdingQuantities.put(order.getStockCode(), holdingQuantities.getOrDefault(order.getStockCode(), BigDecimal.ZERO).add(qty));
            } else if ("SELL".equalsIgnoreCase(order.getOrderType())) {
                holdingQuantities.put(order.getStockCode(), holdingQuantities.getOrDefault(order.getStockCode(), BigDecimal.ZERO).subtract(qty));
            }
        }

        // 보유 주식의 실시간 가치 합산 (수량 * 현재가)
        BigDecimal totalStockEvaluation = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : holdingQuantities.entrySet()) {
            BigDecimal qty = entry.getValue();
            if (qty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal currentPrice = currentPrices.get(entry.getKey());
                if (currentPrice != null) {
                    totalStockEvaluation = totalStockEvaluation.add(qty.multiply(currentPrice));
                }
            }
        }

        currentAsset.setStockEvaluation(totalStockEvaluation.doubleValue());
        currentAsset.setTotalAsset(currentAsset.getCashBalance() + totalStockEvaluation.doubleValue());
        assetHistoryRepository.save(currentAsset);
    }

    /**
     * 한국 주식시장 정규장(평일 09:00 ~ 15:30) 운영 여부 체크
     */
    private boolean isKoreanMarketOpen() {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek day = now.getDayOfWeek();
        
        // 주말 스킵
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }

        LocalTime time = now.toLocalTime();
        LocalTime openTime = LocalTime.of(9, 0);
        LocalTime closeTime = LocalTime.of(15, 30);

        return !time.isBefore(openTime) && !time.isAfter(closeTime);
    }

    /**
     * 실시간 지능형 손절/익절 작동 판정 및 매도 청산 실행
     */
    private boolean checkAndExecuteRiskControl(TradingBot bot, BigDecimal currentPrice, List<OrderRecord> orderHistory) {
        if (bot.getStopLossPercent() == null && bot.getTakeProfitPercent() == null) {
            return false;
        }

        // 해당 종목의 현재 보유량과 평단가 추적
        double totalQty = 0.0;
        double totalCost = 0.0;
        double avgPrice = 0.0;

        // 오래된 순으로 정렬하여 평균 단가 산정
        List<OrderRecord> symbolOrders = orderHistory.stream()
                .filter(o -> o.getStockCode().equals(bot.getStockCode()) && "FILLED".equalsIgnoreCase(o.getStatus()))
                .sorted(java.util.Comparator.comparing(OrderRecord::getOrderTime))
                .toList();

        for (OrderRecord o : symbolOrders) {
            if ("BUY".equalsIgnoreCase(o.getOrderType())) {
                totalQty += o.getQuantity();
                totalCost += o.getQuantity() * o.getPrice();
                avgPrice = totalQty > 0 ? totalCost / totalQty : 0.0;
            } else if ("SELL".equalsIgnoreCase(o.getOrderType())) {
                totalQty = Math.max(0.0, totalQty - o.getQuantity());
                totalCost = totalQty * avgPrice;
                if (totalQty == 0.0) {
                    avgPrice = 0.0;
                    totalCost = 0.0;
                }
            }
        }

        if (totalQty <= 0.0) {
            return false;
        }

        // 현재 수익률(%) 계산
        double returnRate = ((currentPrice.doubleValue() - avgPrice) / avgPrice) * 100.0;

        boolean isStopLoss = bot.getStopLossPercent() != null && returnRate <= -bot.getStopLossPercent();
        boolean isTakeProfit = bot.getTakeProfitPercent() != null && returnRate >= bot.getTakeProfitPercent();

        if (isStopLoss || isTakeProfit) {
            String triggerType = isStopLoss ? "🚨 지능형 손절" : "🎯 지능형 익절";
            System.out.printf("[%s 발동] %s (%s) 현재가 %s, 평단가 %s (수익률 %.2f%%)%n", 
                    triggerType, bot.getStockName(), bot.getStockCode(), currentPrice, avgPrice, returnRate);
            try {
                OrderRecord filledOrder = orderService.executeSell(bot, currentPrice);
                if (filledOrder != null) {
                    // 알림 메세지 구성
                    String alertMessage = String.format(
                            "<b>[%s 발동]</b>\n" +
                            "<b>종목명:</b> %s (%s)\n" +
                            "<b>구분:</b> 전량 청산 매도\n" +
                            "<b>체결가:</b> %,.0f 원\n" +
                            "<b>평단가:</b> %,.0f 원\n" +
                            "<b>수익률:</b> %.2f%%", 
                            triggerType, bot.getStockName(), bot.getStockCode(), currentPrice, avgPrice, returnRate);
                    notificationService.sendTelegramMessage(alertMessage);
                    return true;
                }
            } catch (Exception e) {
                System.err.println("Risk control sell execution error for " + bot.getStockCode() + ": " + e.getMessage());
            }
        }

        return false;
    }

    /**
     * 포트폴리오 비중 리밸런싱을 연산하고 매매 주문을 수행합니다.
     */
    private void executePortfolioRebalancing(TradingBot portfolioBot, Map<String, BigDecimal> currentPrices) {
        System.out.println("[Rebalancing] Executing portfolio rebalancing check...");
        try {
            // 1. 목표 비중 정보 로드 (예: {"005930": 50, "000660": 50})
            Map<String, Double> targets = objectMapper.readValue(portfolioBot.getParameters(), new TypeReference<>() {});
            if (targets == null || targets.isEmpty()) {
                return;
            }

            // 2. 현재 자산 정보 및 보유 수량 정산
            AssetHistory currentAsset = assetHistoryRepository.findFirstByOrderByRecordTimeDesc().orElse(null);
            if (currentAsset == null) {
                return;
            }
            double cash = currentAsset.getCashBalance();

            // 전체 거래 내역을 바탕으로 개별 보유량 계산
            List<OrderRecord> orders = orderRepository.findAllByOrderByOrderTimeDesc();
            Map<String, Double> holdings = new HashMap<>();
            for (OrderRecord order : orders) {
                if (!"FILLED".equalsIgnoreCase(order.getStatus())) continue;
                double qty = order.getQuantity();
                String code = order.getStockCode();
                if ("BUY".equalsIgnoreCase(order.getOrderType())) {
                    holdings.put(code, holdings.getOrDefault(code, 0.0) + qty);
                } else if ("SELL".equalsIgnoreCase(order.getOrderType())) {
                    holdings.put(code, holdings.getOrDefault(code, 0.0) - qty);
                }
            }

            // 총 자산(예수금 + 주식 평가액) 계산
            double totalAsset = cash;
            for (Map.Entry<String, Double> entry : holdings.entrySet()) {
                String symbol = entry.getKey();
                double qty = entry.getValue();
                if (qty > 0 && currentPrices.containsKey(symbol)) {
                    totalAsset += qty * currentPrices.get(symbol).doubleValue();
                }
            }

            // 3. 종목별 목표 가치 및 수량 계산하여 편차 산출
            Map<String, Double> diffQtyMap = new HashMap<>();
            for (Map.Entry<String, Double> entry : targets.entrySet()) {
                String symbol = entry.getKey();
                double weight = entry.getValue();
                BigDecimal price = currentPrices.get(symbol);
                if (price == null || price.doubleValue() <= 0) continue;

                double targetValue = totalAsset * (weight / 100.0);
                double targetQty = targetValue / price.doubleValue();
                double currentQty = holdings.getOrDefault(symbol, 0.0);
                double diffQty = targetQty - currentQty;

                diffQtyMap.put(symbol, diffQty);
            }

            StringBuilder report = new StringBuilder("<b>[⚖️ 포트폴리오 리밸런싱 완료]</b>\n");
            boolean executed = false;

            // 4-1. 초과 비중 종목 매도 선행 (예수금 확보)
            for (Map.Entry<String, Double> entry : diffQtyMap.entrySet()) {
                String symbol = entry.getKey();
                double diffQty = entry.getValue();
                BigDecimal price = currentPrices.get(symbol);

                // 최소 1주 이상 편차가 있을 때 매매 격발 (소수점 자투리 주문 방지)
                if (diffQty <= -1.0) {
                    double sellQty = Math.floor(Math.abs(diffQty));
                    TradingBot tempBot = new TradingBot();
                    tempBot.setStockCode(symbol);
                    tempBot.setStockName("리밸런싱(" + symbol + ")");
                    tempBot.setAlgorithmType("REBALANCING");

                    System.out.printf("[Rebalance Sell] %s %s주 매도 격발%n", symbol, sellQty);
                    // MockOrderService가 봇의 stockCode를 기준으로 작동하도록 가상 매도 주문 호출
                    OrderRecord order = orderService.executeSell(tempBot, price);
                    if (order != null) {
                        report.append(String.format("- %s: 매도 %.0f주 (%,.0f원)\n", symbol, sellQty, price.doubleValue() * sellQty));
                        executed = true;
                    }
                }
            }

            // DB 잔고 재로딩 (매도로 증가된 현금 반영)
            AssetHistory updatedAsset = assetHistoryRepository.findFirstByOrderByRecordTimeDesc().orElse(currentAsset);
            double updatedCash = updatedAsset.getCashBalance();

            // 4-2. 부족 비중 종목 매수 후행
            for (Map.Entry<String, Double> entry : diffQtyMap.entrySet()) {
                String symbol = entry.getKey();
                double diffQty = entry.getValue();
                BigDecimal price = currentPrices.get(symbol);

                if (diffQty >= 1.0) {
                    double buyQty = Math.floor(diffQty);
                    double buyAmount = buyQty * price.doubleValue();

                    if (updatedCash >= buyAmount) {
                        TradingBot tempBot = new TradingBot();
                        tempBot.setStockCode(symbol);
                        tempBot.setStockName("리밸런싱(" + symbol + ")");
                        tempBot.setAlgorithmType("REBALANCING");

                        System.out.printf("[Rebalance Buy] %s %s주 매수 격발 (금액: %s)%n", symbol, buyQty, buyAmount);
                        OrderRecord order = orderService.executeBuy(tempBot, price, BigDecimal.valueOf(buyAmount));
                        if (order != null) {
                            report.append(String.format("- %s: 매수 %.0f주 (%,.0f원)\n", symbol, buyQty, buyAmount));
                            updatedCash -= buyAmount;
                            executed = true;
                        }
                    }
                }
            }

            if (executed) {
                notificationService.sendTelegramMessage(report.toString());
            } else {
                System.out.println("[Rebalancing] Assets already balanced within target thresholds.");
            }

        } catch (Exception e) {
            System.err.println("Portfolio rebalancing execution error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 봇의 현재 감시 조건 및 실시간 지표(평단가, 수익률, 다음 매수시간 등)를 조합해 친절한 감시 피드백 메세지를 생성합니다.
     */
    private String generateStatusMessage(TradingBot bot, BigDecimal currentPrice, List<OrderRecord> orderHistory, TossApiClient apiClient) {
        if (!bot.isActive()) {
            return "정지됨 / 감시 대기";
        }

        StringBuilder sb = new StringBuilder();

        // 1. 공통: 지능형 평단가 및 수익률 정보 계산
        double avgPrice = 0.0;
        double totalQty = 0.0;
        double totalCost = 0.0;
        List<OrderRecord> symbolOrders = orderHistory.stream()
                .filter(o -> o.getStockCode().equals(bot.getStockCode()) && "FILLED".equalsIgnoreCase(o.getStatus()))
                .sorted(java.util.Comparator.comparing(OrderRecord::getOrderTime))
                .toList();

        for (OrderRecord o : symbolOrders) {
            if ("BUY".equalsIgnoreCase(o.getOrderType())) {
                totalQty += o.getQuantity();
                totalCost += o.getQuantity() * o.getPrice();
                avgPrice = totalQty > 0 ? totalCost / totalQty : 0.0;
            } else if ("SELL".equalsIgnoreCase(o.getOrderType())) {
                totalQty = Math.max(0.0, totalQty - o.getQuantity());
                totalCost = totalQty * avgPrice;
                if (totalQty == 0.0) {
                    avgPrice = 0.0;
                    totalCost = 0.0;
                }
            }
        }

        boolean hasHoldings = totalQty > 0.0;
        double returnRate = 0.0;
        if (hasHoldings) {
            returnRate = ((currentPrice.doubleValue() - avgPrice) / avgPrice) * 100.0;
        }

        // 2. 알고리즘별 피드백 메시지 생성
        if ("ACCUMULATION".equalsIgnoreCase(bot.getAlgorithmType())) {
            try {
                Map<String, Object> params = objectMapper.readValue(bot.getParameters(), new TypeReference<>() {});
                int interval = ((Number) params.getOrDefault("intervalMinutes", 1440)).intValue();
                
                OrderRecord lastBuy = symbolOrders.stream()
                        .filter(o -> "BUY".equalsIgnoreCase(o.getOrderType()))
                        .reduce((first, second) -> second)
                        .orElse(null);

                if (lastBuy == null) {
                    sb.append("첫 적립 매수 조건 감시 중");
                } else {
                    LocalDateTime nextBuy = lastBuy.getOrderTime().plusMinutes(interval);
                    sb.append(String.format("적립식 매수 완료 | 다음 매수 예정: %02d-%02d %02d:%02d",
                            nextBuy.getMonthValue(), nextBuy.getDayOfMonth(), nextBuy.getHour(), nextBuy.getMinute()));
                }
            } catch (Exception e) {
                sb.append("적립 매수 주기 계산 대기 중");
            }
        } 
        else if ("DROP_BUY".equalsIgnoreCase(bot.getAlgorithmType())) {
            try {
                Map<String, Object> params = objectMapper.readValue(bot.getParameters(), new TypeReference<>() {});
                double dropPercent = ((Number) params.getOrDefault("dropPercent", 5.0)).doubleValue();
                int lookback = ((Number) params.getOrDefault("lookbackDays", 10)).intValue();
                double targetProfit = ((Number) params.getOrDefault("targetProfitPercent", 10.0)).doubleValue();

                if (hasHoldings) {
                    // 매도 감시
                    double targetSellPrice = avgPrice * (1 + targetProfit / 100.0);
                    sb.append(String.format("보유 중 (평단 ₩%,.0f원, %.1f%%) | 익절 목표가 ₩%,.0f원 대기 중", 
                            avgPrice, returnRate, targetSellPrice));
                } else {
                    // 매수 감시
                    List<TossDto.CandleDto> candles = apiClient.getCandles(bot.getStockCode(), "1d", lookback);
                    double peak = candles.stream()
                            .mapToDouble(c -> c.getHigh().doubleValue())
                            .max()
                            .orElse(currentPrice.doubleValue());
                    double targetBuyPrice = peak * (1 - dropPercent / 100.0);
                    double currentDrop = ((peak - currentPrice.doubleValue()) / peak) * 100.0;
                    sb.append(String.format("%d일 최고가 ₩%,.0f원 대비 현재 %.1f%% 하락 (진입 기준 %.1f%% ₩%,.0f원 대기)", 
                            lookback, peak, currentDrop, dropPercent, targetBuyPrice));
                }
            } catch (Exception e) {
                sb.append("낙폭 과대 시세 감시 대기 중");
            }
        } 
        else if ("MA_CROSS".equalsIgnoreCase(bot.getAlgorithmType())) {
            try {
                Map<String, Object> params = objectMapper.readValue(bot.getParameters(), new TypeReference<>() {});
                int shortP = ((Number) params.getOrDefault("shortPeriod", 5)).intValue();
                int longP = ((Number) params.getOrDefault("longPeriod", 20)).intValue();

                List<TossDto.CandleDto> candles = apiClient.getCandles(bot.getStockCode(), "1d", longP + 5);
                if (candles.size() >= longP) {
                    double shortMA = getMA(candles, 0, shortP);
                    double longMA = getMA(candles, 0, longP);

                    if (hasHoldings) {
                        sb.append(String.format("보유 중 (평단 ₩%,.0f원) | 단기선 ₩%,.0f원 / 장기선 ₩%,.0f원 (데드크로스 대기)", 
                                avgPrice, shortMA, longMA));
                    } else {
                        sb.append(String.format("단기선 ₩%,.0f원 / 장기선 ₩%,.0f원 (골든크로스 대기)", 
                                shortMA, longMA));
                    }
                } else {
                    sb.append("이동평균선 데이터 수집 대기 중");
                }
            } catch (Exception e) {
                sb.append("이평선 돌파 시세 감시 대기 중");
            }
        }

        // 3. 지능형 리스크 감시 중인 한도 표시 추가
        if (hasHoldings && (bot.getStopLossPercent() != null || bot.getTakeProfitPercent() != null)) {
            sb.append(" [리스크관리:");
            if (bot.getStopLossPercent() != null) {
                sb.append(String.format(" 손절 -%.1f%%", bot.getStopLossPercent()));
            }
            if (bot.getTakeProfitPercent() != null) {
                sb.append(String.format(" 익절 +%.1f%%", bot.getTakeProfitPercent()));
            }
            sb.append("]");
        }

        return sb.toString();
    }

    private double getMA(List<TossDto.CandleDto> candles, int index, int period) {
        double sum = 0.0;
        int count = 0;
        for (int i = index; i < index + period; i++) {
            if (i >= 0 && i < candles.size()) {
                sum += candles.get(i).getClose().doubleValue();
                count++;
            }
        }
        return count > 0 ? sum / count : 0.0;
    }

    /**
     * 매일 오전 08:40에 발송되는 아침 투자 브리핑 이메일
     */
    @Scheduled(cron = "0 40 8 * * *")
    public void sendMorningBriefing() {
        System.out.println("[Email Report] Generating morning briefing...");
        List<TradingBot> bots = botRepository.findAll();
        if (bots.isEmpty()) return;

        String title = "☀️ [오전 브리핑] 주주님, 오늘 아침 투자 브리핑입니다.";
        String subtitle = "오늘 장 개장(09:00) 전 포트폴리오 감시 종목 동향 분석";

        StringBuilder html = new StringBuilder();
        html.append("<p>좋은 아침입니다, 주주님! 오늘 장 시작 전 등록하신 자동매매 봇들의 상태 및 관심 종목 동향을 보고해 드립니다.</p>");
        
        html.append("<div class='card'>");
        html.append("<h3 class='card-title'>감시 봇 구동 현황</h3>");
        html.append("<ul>");
        for (TradingBot bot : bots) {
            String activeBadge = bot.isActive() ? "<span class='badge-buy'>감시중</span>" : "<span class='badge-sell'>정지됨</span>";
            html.append(String.format("<li><strong>%s (%s):</strong> 전략 %s | 상태: %s</li>",
                    bot.getStockName(), bot.getStockCode(), bot.getAlgorithmType(), activeBadge));
        }
        html.append("</ul>");
        html.append("</div>");

        double cash = 10000000.0;
        double evaluation = 0.0;
        Optional<AssetHistory> latestAsset = assetHistoryRepository.findFirstByOrderByRecordTimeDesc();
        if (latestAsset.isPresent()) {
            cash = latestAsset.get().getCashBalance();
            evaluation = latestAsset.get().getStockEvaluation();
        }
        
        html.append("<div class='card'>");
        html.append("<h3 class='card-title'>자산 잔고 요약</h3>");
        html.append("<ul>");
        html.append(String.format("<li><strong>가용 예수금:</strong> ₩%,.0f원</li>", cash));
        html.append(String.format("<li><strong>보유 주식 평가금액:</strong> ₩%,.0f원</li>", evaluation));
        html.append(String.format("<li><strong>총 평가 자산:</strong> ₩%,.0f원</li>", cash + evaluation));
        html.append("</ul>");
        html.append("</div>");

        html.append("<p style='font-size: 13px; color: #94a3b8;'>※ 매 거래 체결 또는 급락 시에는 실시간 알림 이메일이 추가로 발송됩니다. 오늘도 성투하시기 바랍니다!</p>");

        String content = emailService.buildTemplate(title, subtitle, html.toString());
        emailService.sendHtmlEmail(title, content);
    }

    /**
     * 매일 오후 18:00에 발송되는 저녁 장마감 종합 보고서 이메일
     */
    @Scheduled(cron = "0 0 18 * * *")
    public void sendEveningReport() {
        System.out.println("[Email Report] Generating evening report...");
        
        String title = "🌙 [장마감 보고] 주주님, 오늘 자 마감 종합 리포트입니다.";
        String subtitle = "오늘의 매매 복기 및 최종 계좌 자산 현황 보고";

        StringBuilder html = new StringBuilder();
        
        double cash = 10000000.0;
        double evaluation = 0.0;
        Optional<AssetHistory> latestAsset = assetHistoryRepository.findFirstByOrderByRecordTimeDesc();
        if (latestAsset.isPresent()) {
            cash = latestAsset.get().getCashBalance();
            evaluation = latestAsset.get().getStockEvaluation();
        }
        double totalAsset = cash + evaluation;

        html.append("<div class='card'>");
        html.append("<h3 class='card-title'>오늘의 최종 자산 요약</h3>");
        html.append("<ul>");
        html.append(String.format("<li><strong>가용 예수금:</strong> ₩%,.0f원</li>", cash));
        html.append(String.format("<li><strong>보유 주식 평가금액:</strong> ₩%,.0f원</li>", evaluation));
        html.append(String.format("<li><strong>총 평가 자산:</strong> ₩%,.0f원</li>", totalAsset));
        html.append("</ul>");
        html.append("</div>");

        LocalDateTime startOfToday = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        List<OrderRecord> todayOrders = orderRepository.findAllByOrderByOrderTimeDesc().stream()
                .filter(o -> o.getOrderTime().isAfter(startOfToday) && "FILLED".equalsIgnoreCase(o.getStatus()))
                .toList();

        html.append("<div class='card'>");
        html.append("<h3 class='card-title'>오늘의 매매 체결 기록</h3>");
        if (todayOrders.isEmpty()) {
            html.append("<p style='color: #94a3b8; font-style: italic;'>금일 자동매매 체결 이력이 존재하지 않습니다.</p>");
        } else {
            html.append("<table style='width: 100%; border-collapse: collapse; font-size: 14px; text-align: left;'>");
            html.append("<thead><tr style='color: #38bdf8; border-bottom: 1px solid #334155;'><th>종목</th><th>타입</th><th>단가</th><th>수량</th><th>시간</th></tr></thead>");
            html.append("<tbody>");
            for (OrderRecord o : todayOrders) {
                String badge = "BUY".equalsIgnoreCase(o.getOrderType()) ? "<span class='badge-buy'>매수</span>" : "<span class='badge-sell'>매도</span>";
                String timeStr = o.getOrderTime().toLocalTime().toString().substring(0, 5);
                html.append(String.format("<tr style='border-bottom: 1px solid #1e293b;'><td style='padding: 8px 0;'>%s</td><td>%s</td><td>₩%,.0f원</td><td>%,.0f주</td><td>%s</td></tr>",
                        o.getStockName(), badge, o.getPrice(), o.getQuantity(), timeStr));
            }
            html.append("</tbody></table>");
        }
        html.append("</div>");

        html.append("<p style='font-size: 13px; color: #94a3b8;'>※ 본 자동매매 보고서는 주주님의 토스증권 API 계좌 데이터를 기초로 작성되었습니다. 내일도 안정적인 자산 증식을 지원하겠습니다.</p>");

        String content = emailService.buildTemplate(title, subtitle, html.toString());
        emailService.sendHtmlEmail(title, content);
    }
}
