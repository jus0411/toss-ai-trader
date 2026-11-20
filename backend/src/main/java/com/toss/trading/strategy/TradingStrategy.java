package com.toss.trading.strategy;

import com.toss.trading.domain.TradingBot;
import com.toss.trading.domain.OrderRecord;
import com.toss.trading.toss.TossApiClient;
import java.math.BigDecimal;
import java.util.List;

public interface TradingStrategy {
    
    /**
     * 해당 전략 이름 (예: ACCUMULATION, DROP_BUY, MA_CROSS)
     */
    String getStrategyName();

    /**
     * 매수 진입 조건 평가
     */
    boolean shouldBuy(TradingBot bot, BigDecimal currentPrice, List<OrderRecord> orderHistory, TossApiClient apiClient);

    /**
     * 매도 청산 조건 평가
     */
    boolean shouldSell(TradingBot bot, BigDecimal currentPrice, List<OrderRecord> orderHistory, TossApiClient apiClient);
}
