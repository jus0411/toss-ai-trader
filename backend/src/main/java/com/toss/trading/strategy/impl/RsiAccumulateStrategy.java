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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class RsiAccumulateStrategy implements TradingStrategy {

    private final ObjectMapper objectMapper;

    public RsiAccumulateStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getStrategyName() {
        return "RSI_ACCUMULATE";
    }

    @Override
    public boolean shouldBuy(TradingBot bot, BigDecimal currentPrice, List<OrderRecord> orderHistory, TossApiClient apiClient) {
        try {
            Map<String, Object> params = objectMapper.readValue(bot.getParameters(), new TypeReference<>() {});
            int rsiPeriod = ((Number) params.getOrDefault("rsiPeriod", 14)).intValue();
            double rsiThreshold = ((Number) params.getOrDefault("rsiThreshold", 30.0)).doubleValue();

            // RSI 계산을 위해서는 기간보다 1~2일 더 많은 데이터가 필요함 (전일 차이 계산용)
            List<TossDto.CandleDto> candles = apiClient.getCandles(bot.getStockCode(), "1d", rsiPeriod + 5);
            if (candles == null || candles.size() < rsiPeriod) {
                System.out.println("[RsiAccumulateStrategy] Insufficient candle data for " + bot.getStockCode());
                return false;
            }

            // 날짜 순으로 정렬 (과거 -> 최근)
            List<TossDto.CandleDto> sortedCandles = new java.util.ArrayList<>(candles);
            java.util.Collections.reverse(sortedCandles);

            double rsi = calculateRsi(sortedCandles, rsiPeriod);
            System.out.printf("[RsiAccumulateStrategy] %s: CurrentPrice=%s, Computed RSI=%f (Threshold=%f)%n",
                    bot.getStockCode(), currentPrice, rsi, rsiThreshold);

            if (rsi > 0 && rsi <= rsiThreshold) {
                // 당일 중복 매수 방지
                LocalDateTime startOfToday = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
                boolean alreadyBoughtToday = orderHistory.stream()
                        .anyMatch(order -> order.getStockCode().equals(bot.getStockCode())
                                && "BUY".equalsIgnoreCase(order.getOrderType())
                                && "FILLED".equalsIgnoreCase(order.getStatus())
                                && order.getOrderTime().isAfter(startOfToday));

                return !alreadyBoughtToday;
            }

            return false;
        } catch (Exception e) {
            System.err.println("Error evaluating RsiAccumulateStrategy for " + bot.getStockCode() + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean shouldSell(TradingBot bot, BigDecimal currentPrice, List<OrderRecord> orderHistory, TossApiClient apiClient) {
        try {
            Map<String, Object> params = objectMapper.readValue(bot.getParameters(), new TypeReference<>() {});
            if (params.containsKey("targetProfitPercent")) {
                double targetProfitPercent = ((Number) params.get("targetProfitPercent")).doubleValue();

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
            System.err.println("Error evaluating RsiAccumulateStrategy Sell check: " + e.getMessage());
        }
        return false;
    }

    /**
     * Wilder's RSI 계산식
     */
    private double calculateRsi(List<TossDto.CandleDto> candles, int period) {
        int n = candles.size();
        if (n <= period) return 0.0;

        double[] changes = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            BigDecimal closeCur = candles.get(i + 1).getClose();
            BigDecimal closePrev = candles.get(i).getClose();
            if (closeCur == null || closePrev == null) {
                changes[i] = 0.0;
            } else {
                changes[i] = closeCur.subtract(closePrev).doubleValue();
            }
        }

        double sumGain = 0.0;
        double sumLoss = 0.0;

        // 첫 번째 평균 계산 (단순 평균)
        for (int i = 0; i < period; i++) {
            if (changes[i] > 0) {
                sumGain += changes[i];
            } else {
                sumLoss += Math.abs(changes[i]);
            }
        }

        double avgGain = sumGain / period;
        double avgLoss = sumLoss / period;

        // Wilder's Smoothing 적용
        for (int i = period; i < changes.length; i++) {
            double change = changes[i];
            double gain = change > 0 ? change : 0.0;
            double loss = change < 0 ? Math.abs(change) : 0.0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }

        if (avgLoss == 0.0) {
            return 100.0;
        }

        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }
}
