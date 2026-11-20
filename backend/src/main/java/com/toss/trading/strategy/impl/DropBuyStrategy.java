package com.toss.trading.strategy.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toss.trading.domain.OrderRecord;
import com.toss.trading.domain.TradingBot;
import com.toss.trading.strategy.TradingStrategy;
import com.toss.trading.toss.TossApiClient;
import com.toss.trading.toss.dto.TossDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class DropBuyStrategy implements TradingStrategy {

    private final ObjectMapper objectMapper;

    public DropBuyStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getStrategyName() {
        return "DROP_BUY";
    }

    @Override
    public boolean shouldBuy(TradingBot bot, BigDecimal currentPrice, List<OrderRecord> orderHistory, TossApiClient apiClient) {
        try {
            Map<String, Object> params = objectMapper.readValue(bot.getParameters(), new TypeReference<>() {});
            double dropPercent = ((Number) params.getOrDefault("dropPercent", 5.0)).doubleValue();
            int lookbackDays = ((Number) params.getOrDefault("lookbackDays", 10)).intValue();

            // 최근 10일 일봉(1d) 캔들 데이터 조회
            List<TossDto.CandleDto> candles = apiClient.getCandles(bot.getStockCode(), "1d", lookbackDays);
            if (candles == null || candles.isEmpty()) {
                System.out.println("No candle data available for DropBuyStrategy on " + bot.getStockCode());
                return false;
            }

            // 최근 기간 동안의 최고가(High) 찾기
            BigDecimal highestPrice = candles.stream()
                    .map(TossDto.CandleDto::getHigh)
                    .filter(high -> high != null)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            if (highestPrice.compareTo(BigDecimal.ZERO) == 0) {
                return false;
            }

            // 하락 기준 가격 계산 (최고가 * (1 - dropPercent / 100))
            BigDecimal dropRatio = BigDecimal.valueOf(1.0 - (dropPercent / 100.0));
            BigDecimal thresholdPrice = highestPrice.multiply(dropRatio);

            System.out.printf("[DropBuyStrategy] %s: Current=%s, Highest=%s, Threshold=%s (Drop=%s%%)%n",
                    bot.getStockCode(), currentPrice, highestPrice, thresholdPrice, dropPercent);

            // 현재가가 기준 가격 이하로 하락했는지 판단
            if (currentPrice.compareTo(thresholdPrice) <= 0) {
                // 당일 이미 매수한 이력이 있다면 중복 매수를 방지하기 위해 오늘 하루는 건너뜀
                LocalDateTime startOfToday = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
                boolean alreadyBoughtToday = orderHistory.stream()
                        .anyMatch(order -> order.getStockCode().equals(bot.getStockCode())
                                && "BUY".equalsIgnoreCase(order.getOrderType())
                                && "FILLED".equalsIgnoreCase(order.getStatus())
                                && order.getOrderTime().isAfter(startOfToday));

                return !alreadyBoughtToday; // 오늘 이미 샀으면 false, 안 샀으면 true
            }

            return false;

        } catch (Exception e) {
            System.err.println("Error evaluating DropBuyStrategy for " + bot.getStockCode() + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean shouldSell(TradingBot bot, BigDecimal currentPrice, List<OrderRecord> orderHistory, TossApiClient apiClient) {
        // DropBuy 전략도 매수는 시스템이 하되, 매도는 수동 혹은 다른 전략(수익 실현률 도달)으로 확장 처리할 수 있음. 기본은 false.
        try {
            Map<String, Object> params = objectMapper.readValue(bot.getParameters(), new TypeReference<>() {});
            if (params.containsKey("targetProfitPercent")) {
                double targetProfitPercent = ((Number) params.get("targetProfitPercent")).doubleValue();

                // DB 주문기록 혹은 토스 평단가를 바탕으로 평단가 구하기
                // 여기서는 평단가 대비 현재가 상승률로 익절 판단
                // 시간 순서대로 정렬하여 현재 잔고에 해당하는 평단가 추적
                List<OrderRecord> symbolOrders = orderHistory.stream()
                        .filter(o -> o.getStockCode().equals(bot.getStockCode()) && "FILLED".equalsIgnoreCase(o.getStatus()))
                        .sorted(java.util.Comparator.comparing(OrderRecord::getOrderTime))
                        .toList();

                double totalQty = 0.0;
                double totalCost = 0.0;
                double avgPrice = 0.0;

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

                if (totalQty <= 0.0 || avgPrice <= 0.0) return false;

                BigDecimal averagePrice = BigDecimal.valueOf(avgPrice);
                BigDecimal profitRatio = BigDecimal.valueOf(1.0 + (targetProfitPercent / 100.0));
                BigDecimal targetSellPrice = averagePrice.multiply(profitRatio);

                return currentPrice.compareTo(targetSellPrice) >= 0;
            }
        } catch (Exception e) {
            System.err.println("Error evaluating DropBuyStrategy Sell check: " + e.getMessage());
        }
        return false;
    }
}
