package com.toss.trading.strategy.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toss.trading.domain.OrderRecord;
import com.toss.trading.domain.TradingBot;
import com.toss.trading.strategy.TradingStrategy;
import com.toss.trading.toss.TossApiClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class AccumulationStrategy implements TradingStrategy {

    private final ObjectMapper objectMapper;

    public AccumulationStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getStrategyName() {
        return "ACCUMULATION";
    }

    @Override
    public boolean shouldBuy(TradingBot bot, BigDecimal currentPrice, List<OrderRecord> orderHistory, TossApiClient apiClient) {
        try {
            Map<String, Object> params = objectMapper.readValue(bot.getParameters(), new TypeReference<>() {});
            
            // intervalMinutes 파라미터가 없으면 기본값 1440분(1일) 사용
            int intervalMinutes = ((Number) params.getOrDefault("intervalMinutes", 1440)).intValue();

            // 이 봇(종목코드)의 가장 최근 매수 성공 기록 조회
            OrderRecord lastBuyOrder = orderHistory.stream()
                    .filter(order -> order.getStockCode().equals(bot.getStockCode()) 
                            && "BUY".equalsIgnoreCase(order.getOrderType())
                            && "FILLED".equalsIgnoreCase(order.getStatus()))
                    .findFirst()
                    .orElse(null);

            if (lastBuyOrder == null) {
                // 한 번도 매수한 적이 없으면 즉시 매수
                return true;
            }

            // 마지막 매수 시점으로부터 경과 시간 계산
            LocalDateTime lastOrderTime = lastBuyOrder.getOrderTime();
            long minutesPassed = Duration.between(lastOrderTime, LocalDateTime.now()).toMinutes();

            return minutesPassed >= intervalMinutes;

        } catch (Exception e) {
            System.err.println("Error evaluating AccumulationStrategy for " + bot.getStockCode() + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean shouldSell(TradingBot bot, BigDecimal currentPrice, List<OrderRecord> orderHistory, TossApiClient apiClient) {
        // 적립식 매수는 자동으로 매도하지 않음
        return false;
    }
}
