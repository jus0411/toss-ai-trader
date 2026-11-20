package com.toss.trading.toss;

import com.toss.trading.domain.OrderRecord;
import com.toss.trading.domain.TradingBot;
import java.math.BigDecimal;

public interface TossOrderService {
    
    /**
     * 지정된 금액(amount) 내에서 현재가(price) 기준으로 정수 주 수량을 계산하여 매수를 실행합니다.
     */
    OrderRecord executeBuy(TradingBot bot, BigDecimal price, BigDecimal amount);

    /**
     * 보유한 주식을 전량 매도합니다.
     */
    OrderRecord executeSell(TradingBot bot, BigDecimal price);
}
