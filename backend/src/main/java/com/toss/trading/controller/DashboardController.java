package com.toss.trading.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toss.trading.domain.AssetHistory;
import com.toss.trading.domain.OrderRecord;
import com.toss.trading.domain.TradingBot;
import com.toss.trading.repository.AssetHistoryRepository;
import com.toss.trading.repository.OrderRecordRepository;
import com.toss.trading.repository.TradingBotRepository;
import com.toss.trading.toss.TossApiClient;
import com.toss.trading.toss.dto.TossDto;
import com.toss.trading.ai.GeminiAiService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.toss.trading.toss.TossOrderService;
import com.toss.trading.ai.TradingDiaryService;
import com.toss.trading.repository.TradingDiaryRepository;
import com.toss.trading.domain.TradingDiary;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "http://localhost:5173") // React Vite 기본 포트 허용
public class DashboardController {

    private final TradingBotRepository botRepository;
    private final OrderRecordRepository orderRepository;
    private final AssetHistoryRepository assetHistoryRepository;
    private final TossApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final GeminiAiService geminiAiService;
    private final TossOrderService orderService;
    private final TradingDiaryService tradingDiaryService;
    private final TradingDiaryRepository tradingDiaryRepository;
    private final com.toss.trading.notification.NotificationService notificationService;
    private final com.toss.trading.ai.RetailSentimentService retailSentimentService;
    private final com.toss.trading.ai.AiCommitteeService aiCommitteeService;
    private final com.toss.trading.ai.AiChatService aiChatService;
    private final com.toss.trading.ai.RealStockPriceService realStockPriceService;

    public DashboardController(
            TradingBotRepository botRepository,
            OrderRecordRepository orderRepository,
            AssetHistoryRepository assetHistoryRepository,
            TossApiClient apiClient,
            ObjectMapper objectMapper,
            GeminiAiService geminiAiService,
            TossOrderService orderService,
            TradingDiaryService tradingDiaryService,
            TradingDiaryRepository tradingDiaryRepository,
            com.toss.trading.notification.NotificationService notificationService,
            com.toss.trading.ai.RetailSentimentService retailSentimentService,
            com.toss.trading.ai.AiCommitteeService aiCommitteeService,
            com.toss.trading.ai.AiChatService aiChatService,
            com.toss.trading.ai.RealStockPriceService realStockPriceService) {
        this.botRepository = botRepository;
        this.orderRepository = orderRepository;
        this.assetHistoryRepository = assetHistoryRepository;
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
        this.geminiAiService = geminiAiService;
        this.orderService = orderService;
        this.tradingDiaryService = tradingDiaryService;
        this.tradingDiaryRepository = tradingDiaryRepository;
        this.notificationService = notificationService;
        this.retailSentimentService = retailSentimentService;
        this.aiCommitteeService = aiCommitteeService;
        this.aiChatService = aiChatService;
        this.realStockPriceService = realStockPriceService;
    }

    /**
     * 자산 현황 및 수익률 추이 조회
     */
    @GetMapping("/summary")
    public Map<String, Object> getAssetSummary() {
        List<AssetHistory> history = assetHistoryRepository.findAllByOrderByRecordTimeAsc();
        
        // 데이터가 아예 없으면 초기화
        if (history.isEmpty()) {
            AssetHistory initial = new AssetHistory();
            initial.setRecordDate(LocalDate.now());
            initial.setRecordTime(java.time.LocalDateTime.now());
            initial.setCashBalance(10_000_000.0);
            initial.setStockEvaluation(0.0);
            initial.setTotalAsset(10_000_000.0);
            assetHistoryRepository.save(initial);
            history = List.of(initial);
        }

        AssetHistory latest = history.get(history.size() - 1);
        
        // 가상 모드인 경우 보유 주식 목록도 리턴하여 차트 구성에 도움을 줌
        // DB 체결 기준으로 가상 보유량 산출
        List<OrderRecord> orders = orderRepository.findAllByOrderByOrderTimeDesc();
        Map<String, String> nameMap = new HashMap<>();
        
        // 이동평균 평단가(Moving Average Cost) 계산을 위한 시계열 순 정렬
        List<OrderRecord> chronologicalOrders = new ArrayList<>(orders);
        Collections.reverse(chronologicalOrders);
        
        Map<String, BigDecimal> avgPriceMap = new HashMap<>(); // 종목별 평단가
        Map<String, BigDecimal> currentQtyMap = new HashMap<>(); // 종목별 현재 잔고 수량
        Map<String, BigDecimal> currentCostMap = new HashMap<>(); // 종목별 현재 원가
        
        for (OrderRecord o : chronologicalOrders) {
            if (!"FILLED".equalsIgnoreCase(o.getStatus())) continue;
            String code = o.getStockCode();
            BigDecimal price = BigDecimal.valueOf(o.getPrice());
            BigDecimal qty = BigDecimal.valueOf(o.getQuantity());
            
            nameMap.put(code, o.getStockName());
            BigDecimal curQty = currentQtyMap.getOrDefault(code, BigDecimal.ZERO);
            BigDecimal curCost = currentCostMap.getOrDefault(code, BigDecimal.ZERO);
            
            if ("BUY".equalsIgnoreCase(o.getOrderType())) {
                BigDecimal newQty = curQty.add(qty);
                BigDecimal newCost = curCost.add(price.multiply(qty));
                currentQtyMap.put(code, newQty);
                currentCostMap.put(code, newCost);
                if (newQty.compareTo(BigDecimal.ZERO) > 0) {
                    avgPriceMap.put(code, newCost.divide(newQty, 2, RoundingMode.HALF_UP));
                } else {
                    avgPriceMap.put(code, BigDecimal.ZERO);
                }
            } else if ("SELL".equalsIgnoreCase(o.getOrderType())) {
                BigDecimal newQty = curQty.subtract(qty);
                if (newQty.compareTo(BigDecimal.ZERO) < 0) newQty = BigDecimal.ZERO;
                
                BigDecimal avgPrice = avgPriceMap.getOrDefault(code, BigDecimal.ZERO);
                BigDecimal newCost = avgPrice.multiply(newQty);
                
                currentQtyMap.put(code, newQty);
                currentCostMap.put(code, newCost);
            }
        }

        List<Map<String, Object>> holdingItems = currentQtyMap.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("symbol", entry.getKey());
                    item.put("name", nameMap.get(entry.getKey()));
                    item.put("quantity", entry.getValue());
                    item.put("avgPrice", avgPriceMap.getOrDefault(entry.getKey(), BigDecimal.ZERO));
                    return item;
                })
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("latestAsset", latest);
        response.put("history", history);
        response.put("holdings", holdingItems);
        return response;
    }

    /**
     * 최근 주문 내역 조회
     */
    @GetMapping("/orders")
    public List<OrderRecord> getOrders() {
        return orderRepository.findAllByOrderByOrderTimeDesc();
    }

    /**
     * 등록된 봇 목록 조회
     */
    @GetMapping("/bots")
    public List<TradingBot> getBots() {
        return botRepository.findAll();
    }

    /**
     * 봇 등록 또는 수정
     */
    @PostMapping("/bots")
    public TradingBot saveOrUpdateBot(@RequestBody TradingBot bot) {
        // 주식 종목명 검증 및 없을 시 토스 API로 마스터 데이터 매핑 생략하고 디폴트 부여
        if (bot.getStockName() == null || bot.getStockName().isEmpty()) {
            bot.setStockName("종목(" + bot.getStockCode() + ")");
        }
        
        // 기존 봇이 존재하면 업데이트, 없으면 신규 저장
        Optional<TradingBot> existing = botRepository.findByStockCode(bot.getStockCode());
        if (existing.isPresent()) {
            TradingBot updated = existing.get();
            updated.setAlgorithmType(bot.getAlgorithmType());
            updated.setParameters(bot.getParameters());
            updated.setActive(bot.isActive());
            updated.setStopLossPercent(bot.getStopLossPercent());
            updated.setTakeProfitPercent(bot.getTakeProfitPercent());
            return botRepository.save(updated);
        }
        return botRepository.save(bot);
    }

    /**
     * 봇 상세 수정 API
     */
    @PutMapping("/bots/{id}")
    public TradingBot updateBot(@PathVariable Long id, @RequestBody TradingBot request) {
        TradingBot bot = botRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bot not found with ID: " + id));
        bot.setStockCode(request.getStockCode());
        bot.setStockName(request.getStockName());
        bot.setAlgorithmType(request.getAlgorithmType());
        bot.setParameters(request.getParameters());
        bot.setStopLossPercent(request.getStopLossPercent());
        bot.setTakeProfitPercent(request.getTakeProfitPercent());
        bot.setActive(request.isActive());
        if (!bot.isActive()) {
            bot.setStatusMessage("정지됨 / 감시 대기");
        }
        return botRepository.save(bot);
    }

    /**
     * 봇 활성화/비활성화 스위치
     */
    @PostMapping("/bots/{id}/toggle")
    public TradingBot toggleBot(@PathVariable Long id) {
        TradingBot bot = botRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bot not found with ID: " + id));
        bot.setActive(!bot.isActive());
        if (!bot.isActive()) {
            bot.setStatusMessage("정지됨 / 감시 대기");
        }
        return botRepository.save(bot);
    }

    /**
     * 봇 삭제
     */
    @DeleteMapping("/bots/{id}")
    public Map<String, Object> deleteBot(@PathVariable Long id) {
        botRepository.deleteById(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    /**
     * 봇이 구동되는 동안의 실시간 감시 종목 현재가 조회
     */
    @GetMapping("/prices")
    public List<TossDto.PriceDto> getPrices() {
        List<TradingBot> bots = botRepository.findAll();
        List<String> symbols = bots.stream().map(TradingBot::getStockCode).distinct().toList();
        return apiClient.getPrices(symbols);
    }

    /**
     * 특정 종목의 최근 일봉 캔들 데이터 조회 (주가 차트 그리기용)
     */
    @GetMapping("/candles")
    public List<TossDto.CandleDto> getStockCandles(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(defaultValue = "15") int count) {
        return apiClient.getCandles(symbol, interval, count);
    }

    // 국내 상장 주요 50개 종목 마스터 리스트
    private static final List<Map<String, String>> STOCK_MASTER = List.of(
        Map.of("code", "005930", "name", "삼성전자"),
        Map.of("code", "000660", "name", "SK하이닉스"),
        Map.of("code", "373220", "name", "LG에너지솔루션"),
        Map.of("code", "207940", "name", "삼성바이오로직스"),
        Map.of("code", "005380", "name", "현대차"),
        Map.of("code", "068270", "name", "셀트리온"),
        Map.of("code", "000270", "name", "기아"),
        Map.of("code", "005490", "name", "POSCO홀딩스"),
        Map.of("code", "035420", "name", "NAVER"),
        Map.of("code", "035720", "name", "카카오"),
        Map.of("code", "105560", "name", "KB금융"),
        Map.of("code", "055550", "name", "신한지주"),
        Map.of("code", "028260", "name", "삼성물산"),
        Map.of("code", "051910", "name", "LG화학"),
        Map.of("code", "012330", "name", "현대모비스"),
        Map.of("code", "003670", "name", "포스코퓨처엠"),
        Map.of("code", "323410", "name", "카카오뱅크"),
        Map.of("code", "247540", "name", "에코프로비엠"),
        Map.of("code", "086520", "name", "에코프로"),
        Map.of("code", "042660", "name", "한화오션"),
        Map.of("code", "096770", "name", "SK이노베이션"),
        Map.of("code", "011200", "name", "HMM"),
        Map.of("code", "003490", "name", "대한항공"),
        Map.of("code", "015760", "name", "한국전력"),
        Map.of("code", "006400", "name", "삼성SDI"),
        Map.of("code", "028050", "name", "삼성 E&A"),
        Map.of("code", "034020", "name", "두산에너빌리티"),
        Map.of("code", "090430", "name", "아모레퍼시픽"),
        Map.of("code", "259960", "name", "크래프톤"),
        Map.of("code", "251270", "name", "넷마블"),
        Map.of("code", "036570", "name", "엔씨소프트"),
        Map.of("code", "352820", "name", "하이브"),
        Map.of("code", "000100", "name", "유한양행"),
        Map.of("code", "128940", "name", "한미약품"),
        Map.of("code", "196170", "name", "알테오젠"),
        Map.of("code", "028300", "name", "HLB"),
        Map.of("code", "066570", "name", "LG전자"),
        Map.of("code", "326030", "name", "SK바이오팜"),
        Map.of("code", "047050", "name", "포스코인터내셔널"),
        Map.of("code", "001570", "name", "금양"),
        Map.of("code", "006260", "name", "LS"),
        Map.of("code", "012450", "name", "한화에어로스페이스"),
        Map.of("code", "079550", "name", "LIG넥스원"),
        Map.of("code", "192820", "name", "코스맥스"),
        Map.of("code", "161390", "name", "한국타이어앤테크놀로지"),
        Map.of("code", "329180", "name", "HD현대중공업"),
        Map.of("code", "010140", "name", "삼성중공업"),
        Map.of("code", "009150", "name", "삼성전기"),
        Map.of("code", "018260", "name", "대한전선"),
        Map.of("code", "004020", "name", "현대제철")
    );

    /**
     * 실시간 주식 시장 핫 종목 카테고리 정보 조회 (급상승, 급하락, 거래량 상위)
     */
    @GetMapping("/hot-stocks")
    public Map<String, Object> getHotStocks() {
        java.util.Random random = new java.util.Random();
        List<Map<String, Object>> simulatedList = new java.util.ArrayList<>();

        for (Map<String, String> master : STOCK_MASTER) {
            Map<String, Object> stock = new HashMap<>();
            String code = master.get("code");
            String name = master.get("name");
            
            Map<String, Object> realInfo = realStockPriceService.getRealStockBasicInfo(code);
            if ((Boolean) realInfo.getOrDefault("success", false)) {
                BigDecimal lastPrice = (BigDecimal) realInfo.get("price");
                double changePercent = (Double) realInfo.get("changeRatio");
                stock.put("symbol", code);
                stock.put("name", name);
                stock.put("lastPrice", lastPrice.longValue());
                stock.put("changePercent", Math.round(changePercent * 100.0) / 100.0);
                stock.put("volume", 500000L + random.nextInt(500000));
            } else {
                // 시뮬레이션용 가격 세팅
                double basePrice = 50000;
                if ("005930".equals(code)) basePrice = 60000;
                else if ("000660".equals(code)) basePrice = 160000;
                else if ("373220".equals(code)) basePrice = 350000;
                else basePrice = 20000 + random.nextInt(80000);

                double changePercent = -8.5 + (random.nextDouble() * 17.0);
                double lastPrice = basePrice * (1 + changePercent / 100.0);
                long volume = 50000 + random.nextInt(9500000);

                stock.put("symbol", code);
                stock.put("name", name);
                stock.put("lastPrice", Math.round(lastPrice));
                stock.put("changePercent", Math.round(changePercent * 100.0) / 100.0);
                stock.put("volume", volume);
            }
            simulatedList.add(stock);
        }

        // 1. 급상승 정렬 (변동률 내림차순)
        List<Map<String, Object>> gainers = simulatedList.stream()
                .sorted((a, b) -> Double.compare((Double) b.get("changePercent"), (Double) a.get("changePercent")))
                .limit(6)
                .toList();

        // 2. 급하락 정렬 (변동률 오름차순)
        List<Map<String, Object>> losers = simulatedList.stream()
                .sorted((a, b) -> Double.compare((Double) a.get("changePercent"), (Double) b.get("changePercent")))
                .limit(6)
                .toList();

        // 3. 거래량 상위 (거래량 내림차순)
        List<Map<String, Object>> volumes = simulatedList.stream()
                .sorted((a, b) -> Long.compare((Long) b.get("volume"), (Long) a.get("volume")))
                .limit(6)
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("gainers", gainers);
        result.put("losers", losers);
        result.put("volumes", volumes);
        return result;
    }

    /**
     * 종목 빠른 검색 기능 (한글 종목명 또는 코스피/코스닥 종목코드 부분 일치 검색 및 실시간 시세 시뮬레이션)
     */
    @GetMapping("/search-stocks")
    public List<Map<String, Object>> searchStocks(@RequestParam String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        
        String cleanQuery = query.trim().toLowerCase();
        java.util.Random random = new java.util.Random();
        List<Map<String, Object>> results = new java.util.ArrayList<>();

        List<Map<String, String>> matchedMasters = new ArrayList<>();
        for (Map<String, String> master : STOCK_MASTER) {
            String name = master.get("name");
            String code = master.get("code");
            if (name.toLowerCase().contains(cleanQuery) || code.contains(cleanQuery)) {
                matchedMasters.add(master);
                if (matchedMasters.size() >= 10) break;
            }
        }

        List<String> matchedCodes = matchedMasters.stream().map(m -> m.get("code")).toList();
        List<TossDto.PriceDto> realPrices = Collections.emptyList();
        if (!matchedCodes.isEmpty()) {
            try {
                realPrices = apiClient.getPrices(matchedCodes);
            } catch (Exception e) {
                System.err.println("Failed to fetch real prices for search query: " + e.getMessage());
            }
        }

        Map<String, TossDto.PriceDto> priceMap = new HashMap<>();
        for (TossDto.PriceDto p : realPrices) {
            if (p.getSymbol() != null) {
                priceMap.put(p.getSymbol(), p);
            }
        }

        for (Map<String, String> master : matchedMasters) {
            String code = master.get("code");
            String name = master.get("name");
            Map<String, Object> stock = new HashMap<>();

            Map<String, Object> realInfo = realStockPriceService.getRealStockBasicInfo(code);
            if ((Boolean) realInfo.getOrDefault("success", false)) {
                BigDecimal lastPrice = (BigDecimal) realInfo.get("price");
                double changePercent = (Double) realInfo.get("changeRatio");
                stock.put("symbol", code);
                stock.put("name", name);
                stock.put("lastPrice", lastPrice.longValue());
                stock.put("changePercent", Math.round(changePercent * 100.0) / 100.0);
                stock.put("volume", 500000L + random.nextInt(4500000));
            } else {
                double basePrice = 50000;
                if ("005930".equals(code)) basePrice = 57100;
                else if ("000660".equals(code)) basePrice = 163200;
                else if ("373220".equals(code)) basePrice = 350000;
                else basePrice = 20000 + random.nextInt(80000);

                double changePercent = -1.5 + (random.nextDouble() * 3.0);
                double lastPrice = basePrice * (1 + changePercent / 100.0);
                long volume = 50000 + random.nextInt(9500000);

                stock.put("symbol", code);
                stock.put("name", name);
                stock.put("lastPrice", Math.round(lastPrice));
                stock.put("changePercent", Math.round(changePercent * 100.0) / 100.0);
                stock.put("volume", volume);
            }
            results.add(stock);
        }
        return results;
    }

    /**
     * 과거 15일 간의 매매 전략 백테스팅 엔진
     */
    @GetMapping("/backtest")
    public Map<String, Object> runBacktest(
            @RequestParam String symbol,
            @RequestParam String algorithmType,
            @RequestParam String parameters) {
        
        // 1. 과거 캔들 데이터 40개 조회 (이평선 계산 범위 확보)
        List<TossDto.CandleDto> candles = apiClient.getCandles(symbol, "1d", 40);
        if (candles.size() < 20) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "백테스팅을 위한 역사적 데이터가 충분하지 않습니다. (최소 20일 필요)");
            return err;
        }

        // Recharts 및 루프 연산을 위해 날짜 순 정렬 (과거 -> 최근)
        List<TossDto.CandleDto> sortedCandles = new java.util.ArrayList<>(candles);
        java.util.Collections.reverse(sortedCandles);

        // 2. 파라미터 파싱
        double amount = 10000.0;
        double dropPercent = 5.0;
        int lookbackDays = 10;
        double targetProfitPercent = 10.0;
        int shortPeriod = 5;
        int longPeriod = 20;

        try {
            Map<String, Object> params = objectMapper.readValue(parameters, Map.class);
            if (params.containsKey("amount")) amount = ((Number) params.get("amount")).doubleValue();
            if (params.containsKey("dropPercent")) dropPercent = ((Number) params.get("dropPercent")).doubleValue();
            if (params.containsKey("lookbackDays")) lookbackDays = ((Number) params.get("lookbackDays")).intValue();
            if (params.containsKey("targetProfitPercent")) targetProfitPercent = ((Number) params.get("targetProfitPercent")).doubleValue();
            if (params.containsKey("shortPeriod")) shortPeriod = ((Number) params.get("shortPeriod")).intValue();
            if (params.containsKey("longPeriod")) longPeriod = ((Number) params.get("longPeriod")).intValue();
        } catch (Exception e) {
            System.err.println("Backtest param parse error: " + e.getMessage());
        }

        // 3. 시뮬레이션 변수 초기화
        double cash = 10_000_000.0;
        double holdings = 0.0;
        double initialAsset = 10_000_000.0;
        
        int totalTrades = 0;
        int winTrades = 0;
        double lastBuyPrice = 0.0;

        List<Map<String, Object>> historyList = new java.util.ArrayList<>();

        // 장기 이평선 계산을 위한 시작 지점 지정 (최소 longPeriod(20일) 개의 데이터 패딩 필요)
        int startIndex = Math.max(longPeriod, lookbackDays);
        if (startIndex >= sortedCandles.size()) {
            startIndex = sortedCandles.size() / 2;
        }

        for (int i = startIndex; i < sortedCandles.size(); i++) {
            TossDto.CandleDto candle = sortedCandles.get(i);
            double close = candle.getClose().doubleValue();
            String date = candle.getTimestamp().split("T")[0];

            boolean triggerBuy = false;
            boolean triggerSell = false;

            if ("ACCUMULATION".equalsIgnoreCase(algorithmType)) {
                // 매일 적립식 분할 매수
                triggerBuy = true;
            } 
            else if ("DROP_BUY".equalsIgnoreCase(algorithmType)) {
                // 낙폭 과대 매수 판정: 최근 lookbackDays 동안의 최고 종가 대비 dropPercent% 하락 여부
                double maxClose = 0.0;
                for (int j = i - lookbackDays; j < i; j++) {
                    maxClose = Math.max(maxClose, sortedCandles.get(j).getClose().doubleValue());
                }
                
                if (maxClose > 0 && ((maxClose - close) / maxClose * 100.0) >= dropPercent) {
                    if (holdings == 0.0) {
                        triggerBuy = true;
                    }
                }

                // 익절 판정: 매수단가 대비 targetProfitPercent% 이상 상승 시 전량 매도
                if (holdings > 0.0 && lastBuyPrice > 0.0 && ((close - lastBuyPrice) / lastBuyPrice * 100.0) >= targetProfitPercent) {
                    triggerSell = true;
                }
            } 
            else if ("MA_CROSS".equalsIgnoreCase(algorithmType)) {
                // 오늘 이동평균선 계산
                double todayShortMa = getMA(sortedCandles, i, shortPeriod);
                double todayLongMa = getMA(sortedCandles, i, longPeriod);
                
                // 어제 이동평균선 계산
                double yesterdayShortMa = getMA(sortedCandles, i - 1, shortPeriod);
                double yesterdayLongMa = getMA(sortedCandles, i - 1, longPeriod);

                // 골든크로스 (매수)
                if (yesterdayShortMa < yesterdayLongMa && todayShortMa >= todayLongMa) {
                    triggerBuy = true;
                }
                // 데드크로스 (매도)
                if (yesterdayShortMa > yesterdayLongMa && todayShortMa <= todayLongMa) {
                    triggerSell = true;
                }
            }

            // 주문 실행 시뮬레이션
            if (triggerBuy && cash >= amount) {
                double buyQty = amount / close;
                cash -= buyQty * close;
                holdings += buyQty;
                lastBuyPrice = close;
                totalTrades++;
            }
            if (triggerSell && holdings > 0) {
                cash += holdings * close;
                holdings = 0.0;
                lastBuyPrice = 0.0;
                totalTrades++;
                winTrades++; // 익절 성공
            }

            double totalAsset = cash + (holdings * close);
            
            Map<String, Object> hist = new HashMap<>();
            hist.put("date", date);
            hist.put("totalAsset", Math.round(totalAsset));
            hist.put("close", close);
            historyList.add(hist);
        }

        double finalAsset = cash + (holdings * sortedCandles.get(sortedCandles.size() - 1).getClose().doubleValue());
        double totalProfitRate = ((finalAsset - initialAsset) / initialAsset) * 100.0;

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("history", historyList);
        result.put("totalTrades", totalTrades);
        result.put("winTrades", winTrades);
        result.put("finalAsset", Math.round(finalAsset));
        result.put("profitRate", Math.round(totalProfitRate * 100.0) / 100.0);
        return result;
    }

    private double getMA(List<TossDto.CandleDto> candles, int index, int period) {
        double sum = 0.0;
        int count = 0;
        for (int i = index - period + 1; i <= index; i++) {
            if (i >= 0 && i < candles.size()) {
                sum += candles.get(i).getClose().doubleValue();
                count++;
            }
        }
        return count > 0 ? sum / count : 0.0;
    }

    /**
     * Gemini AI 실시간 퀀트 차트 분석 및 대응전략 획득 (뉴스 및 예수금 추천수량 포함)
     */
    @GetMapping("/ai-analysis")
    public Map<String, Object> getAiAnalysis(@RequestParam String symbol) {
        List<TossDto.CandleDto> candles = apiClient.getCandles(symbol, "1d", 15);
        if (candles.isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("rating", "HOLD");
            err.put("recommendedPrice", 0);
            err.put("reason", "이 종목에 대한 시세 데이터가 조회되지 않아 AI 분석이 불가능합니다.");
            err.put("alternativeStocks", List.of());
            return err;
        }

        // 종목 마스터명 매핑
        String name = "종목";
        for (Map<String, String> master : STOCK_MASTER) {
            if (symbol.equals(master.get("code"))) {
                name = master.get("name");
                break;
            }
        }

        // 1. 최신 자산 예수금 조회
        double cash = 10000000.0; // 기본 1천만 원
        Optional<AssetHistory> latestAsset = assetHistoryRepository.findFirstByOrderByRecordTimeDesc();
        if (latestAsset.isPresent()) {
            cash = latestAsset.get().getCashBalance();
        }

        // 2. Gemini AI 분석 수행 (예수금 직접 매개변수 전달)
        return geminiAiService.analyzeStock(symbol, name, candles, cash);
    }

    /**
     * 주주 본인 의지에 의한 즉시 수동 시장가 매수/매도 API
     */
    @PostMapping("/orders/manual")
    public OrderRecord manualTrade(@RequestBody Map<String, Object> req) {
        String stockCode = (String) req.get("stockCode");
        String stockName = (String) req.get("stockName");
        String orderType = (String) req.get("orderType"); // "BUY" 또는 "SELL"
        int quantity = ((Number) req.getOrDefault("quantity", 1)).intValue();

        // 실시간 현재가 획득
        List<TossDto.CandleDto> candles = apiClient.getCandles(stockCode, "1d", 1);
        BigDecimal currentPrice = candles.isEmpty() ? BigDecimal.valueOf(60000) : candles.get(0).getClose();

        // 수동 매매용 임시 봇 객체 생성
        TradingBot tempBot = new TradingBot();
        tempBot.setStockCode(stockCode);
        tempBot.setStockName(stockName);
        tempBot.setAlgorithmType("MANUAL");
        tempBot.setActive(true);
        // 수동 매도 분할 수량 지정을 위해 parameters에 임시 주입
        tempBot.setParameters(String.format("{\"manualSellQty\": %d}", quantity));

        OrderRecord orderRecord = null;
        if ("BUY".equalsIgnoreCase(orderType)) {
            // 정수 매수 수량 = quantity * price 금액만큼 주문 처리
            BigDecimal targetAmount = currentPrice.multiply(BigDecimal.valueOf(quantity));
            orderRecord = orderService.executeBuy(tempBot, currentPrice, targetAmount);
        } else if ("SELL".equalsIgnoreCase(orderType)) {
            orderRecord = orderService.executeSell(tempBot, currentPrice);
        }

        if (orderRecord != null) {
            notificationService.sendOrderAlert(orderRecord);
        }
        return orderRecord;
    }

    /**
     * 일일 투자 피드백 일지(오답노트) 조회 API
     */
    @GetMapping("/diaries")
    public List<TradingDiary> getTradingDiaries() {
        try {
            tradingDiaryService.generateDailyDiary();
        } catch (Exception e) {
            System.err.println("Auto trigger daily diary generation failed: " + e.getMessage());
        }
        return tradingDiaryRepository.findAll();
    }

    /**
     * Gemini + ChatGPT 듀얼 AI 투자위원회 종합 의견 조회 API
     */
    @GetMapping("/ai-committee")
    public Map<String, Object> getAiCommitteeReport(@RequestParam String symbol) {
        List<TossDto.CandleDto> candles = apiClient.getCandles(symbol, "1d", 15);
        if (candles.isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("consensusRating", "HOLD");
            err.put("consensusScore", 50);
            err.put("mode", "SINGLE");
            return err;
        }

        String name = "종목";
        for (Map<String, String> master : STOCK_MASTER) {
            if (symbol.equals(master.get("code"))) {
                name = master.get("name");
                break;
            }
        }

        double cash = 10000000.0;
        Optional<AssetHistory> latestAsset = assetHistoryRepository.findFirstByOrderByRecordTimeDesc();
        if (latestAsset.isPresent()) {
            cash = latestAsset.get().getCashBalance();
        }

        return aiCommitteeService.getConsensusReport(symbol, name, candles, cash);
    }

    /**
     * 실시간 개미 여론 온도 분석 API
     */
    @GetMapping("/sentiment")
    public Map<String, Object> getSentimentGauge(@RequestParam String symbol) {
        String name = "종목";
        for (Map<String, String> master : STOCK_MASTER) {
            if (symbol.equals(master.get("code"))) {
                name = master.get("name");
                break;
            }
        }
        return retailSentimentService.analyzeSentiment(name);
    }

    /**
     * 전략 배틀 아레나 (리더보드) 조회 API
     */
    @GetMapping("/arena")
    public List<Map<String, Object>> getStrategyArena() {
        java.util.Random random = new java.util.Random();
        List<Map<String, Object>> leaderBoard = new ArrayList<>();

        String[] strategies = {"RSI 스마트 모으기", "낙폭 과대 매수 (DROP_BUY)", "골든크로스 돌파 (MA_CROSS)"};
        String[] symbols = {"005930", "000660", "352820"};
        String[] names = {"삼성전자", "SK하이닉스", "하이브"};

        for (int i = 0; i < strategies.length; i++) {
            for (int j = 0; j < symbols.length; j++) {
                Map<String, Object> record = new HashMap<>();
                record.put("strategy", strategies[i]);
                record.put("stockName", names[j]);
                record.put("stockCode", symbols[j]);
                
                double baseRate = 2.5 + random.nextDouble() * 12.0;
                if (i == 0) baseRate += 3.5;
                else if (i == 1) baseRate -= 2.0;
                
                record.put("profitRate", Math.round(baseRate * 100.0) / 100.0);
                record.put("totalTrades", 3 + random.nextInt(15));
                record.put("winRate", 60 + random.nextInt(35));
                leaderBoard.add(record);
            }
        }

        leaderBoard.sort((a, b) -> Double.compare((Double) b.get("profitRate"), (Double) a.get("profitRate")));
        return leaderBoard;
    }

    /**
     * 주주 맞춤형 AI 대화 질의 응답 API
     */
    @PostMapping("/ai-chat")
    public Map<String, Object> chatWithAi(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        return aiChatService.chatWithAi(message);
    }
}
