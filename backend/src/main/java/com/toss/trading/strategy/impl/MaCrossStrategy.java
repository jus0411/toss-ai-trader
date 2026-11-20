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
import java.util.List;
import java.util.Map;

@Component
public class MaCrossStrategy implements TradingStrategy {

    private final ObjectMapper objectMapper;

    public MaCrossStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getStrategyName() {
        return "MA_CROSS";
    }

    @Override
    public boolean shouldBuy(TradingBot bot, BigDecimal currentPrice, List<OrderRecord> orderHistory, TossApiClient apiClient) {
        try {
            Map<String, Object> params = objectMapper.readValue(bot.getParameters(), new TypeReference<>() {});
            int shortPeriod = ((Number) params.getOrDefault("shortPeriod", 5)).intValue();
            int longPeriod = ((Number) params.getOrDefault("longPeriod", 20)).intValue();

            // 직전 봉의 MA도 계산해야 하므로 longPeriod + 2개의 일봉(1d) 캔들 조회
            List<TossDto.CandleDto> candles = apiClient.getCandles(bot.getStockCode(), "1d", longPeriod + 2);
            if (candles == null || candles.size() < longPeriod + 1) {
                System.out.println("Insufficient candle data for MaCrossStrategy on " + bot.getStockCode());
                return false;
            }

            // 현재 캔들 종가에 실시간 현재가를 덮어씌움 (실시간 판단을 위해)
            // 리스트의 0번째가 가장 최신(현재봉)이라고 가정하고 토스 API 응답 순서에 맞춰 연산합니다.
            // 보통 API 응답은 최신순(0번이 오늘) 혹은 과거순(마지막이 오늘)일 수 있으므로 정렬 상태 확인 필요.
            // 토스증권 API는 최신 캔들이 0번에 위치하는 규격(최신 봉부터 역순 반환)을 따릅니다.
            // 0: 오늘(진행중), 1: 어제, 2: 그저께... 순서
            candles.get(0).setClose(currentPrice);

            // 단기 및 장기 이동평균선(MA) 계산
            // 현재 시점의 MA
            BigDecimal currShortMa = calculateMa(candles, 0, shortPeriod);
            BigDecimal currLongMa = calculateMa(candles, 0, longPeriod);

            // 직전 시점(어제)의 MA
            BigDecimal prevShortMa = calculateMa(candles, 1, shortPeriod);
            BigDecimal prevLongMa = calculateMa(candles, 1, longPeriod);

            if (currShortMa == null || currLongMa == null || prevShortMa == null || prevLongMa == null) {
                return false;
            }

            System.out.printf("[MaCrossStrategy] %s: PrevShortMa=%s, PrevLongMa=%s, CurrShortMa=%s, CurrLongMa=%s%n",
                    bot.getStockCode(), prevShortMa, prevLongMa, currShortMa, currLongMa);

            // 골든 크로스 판단 (단기 이평선이 장기 이평선을 상향 돌파)
            boolean goldenCross = prevShortMa.compareTo(prevLongMa) <= 0 && currShortMa.compareTo(currLongMa) > 0;

            if (goldenCross) {
                // 당일 중복 매수 방지
                java.time.LocalDateTime startOfToday = java.time.LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
                boolean alreadyBoughtToday = orderHistory.stream()
                        .anyMatch(order -> order.getStockCode().equals(bot.getStockCode())
                                && "BUY".equalsIgnoreCase(order.getOrderType())
                                && "FILLED".equalsIgnoreCase(order.getStatus())
                                && order.getOrderTime().isAfter(startOfToday));

                return !alreadyBoughtToday;
            }

            return false;

        } catch (Exception e) {
            System.err.println("Error evaluating MaCrossStrategy Buy for " + bot.getStockCode() + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean shouldSell(TradingBot bot, BigDecimal currentPrice, List<OrderRecord> orderHistory, TossApiClient apiClient) {
        try {
            Map<String, Object> params = objectMapper.readValue(bot.getParameters(), new TypeReference<>() {});
            int shortPeriod = ((Number) params.getOrDefault("shortPeriod", 5)).intValue();
            int longPeriod = ((Number) params.getOrDefault("longPeriod", 20)).intValue();

            List<TossDto.CandleDto> candles = apiClient.getCandles(bot.getStockCode(), "1d", longPeriod + 2);
            if (candles == null || candles.size() < longPeriod + 1) {
                return false;
            }

            candles.get(0).setClose(currentPrice);

            BigDecimal currShortMa = calculateMa(candles, 0, shortPeriod);
            BigDecimal currLongMa = calculateMa(candles, 0, longPeriod);
            BigDecimal prevShortMa = calculateMa(candles, 1, shortPeriod);
            BigDecimal prevLongMa = calculateMa(candles, 1, longPeriod);

            if (currShortMa == null || currLongMa == null || prevShortMa == null || prevLongMa == null) {
                return false;
            }

            // 데드 크로스 판단 (단기 이평선이 장기 이평선을 하향 돌파)
            return prevShortMa.compareTo(prevLongMa) >= 0 && currShortMa.compareTo(currLongMa) < 0;

        } catch (Exception e) {
            System.err.println("Error evaluating MaCrossStrategy Sell for " + bot.getStockCode() + ": " + e.getMessage());
            return false;
        }
    }

    private BigDecimal calculateMa(List<TossDto.CandleDto> candles, int startIndex, int period) {
        if (candles.size() < startIndex + period) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = startIndex; i < startIndex + period; i++) {
            BigDecimal close = candles.get(i).getClose();
            if (close == null) return null;
            sum = sum.add(close);
        }
        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }
}
