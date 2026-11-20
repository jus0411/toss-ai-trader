package com.toss.trading.toss.impl;

import com.toss.trading.domain.OrderRecord;
import com.toss.trading.domain.TradingBot;
import com.toss.trading.repository.OrderRecordRepository;
import com.toss.trading.toss.TossApiClient;
import com.toss.trading.toss.TossOrderService;
import com.toss.trading.toss.dto.TossDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Service("realTossOrderService")
public class RealTossOrderService implements TossOrderService {

    private final TossApiClient apiClient;
    private final OrderRecordRepository orderRecordRepository;

    public RealTossOrderService(
            TossApiClient apiClient,
            OrderRecordRepository orderRecordRepository) {
        this.apiClient = apiClient;
        this.orderRecordRepository = orderRecordRepository;
    }

    @Override
    @Transactional
    public OrderRecord executeBuy(TradingBot bot, BigDecimal price, BigDecimal amount) {
        // 정수 주 단위 매수 수량 계산 (금액 / 현재가)
        BigDecimal qty = amount.divide(price, 0, RoundingMode.DOWN);
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.printf("[Real Order] Failed to buy %s: Amount %s is too low for share price %s%n",
                    bot.getStockCode(), amount, price);
            return null;
        }

        // 시장가 매수 주문 요청 생성
        TossDto.OrderCreateRequest request = new TossDto.OrderCreateRequest(
                UUID.randomUUID().toString(),  // 멱등성 클라이언트 주문 ID
                bot.getStockCode(),
                "BUY",
                "MARKET",
                "DAY",
                qty,
                null,
                false
        );

        System.out.printf("[Real Order] Requesting BUY MARKET for %s, Qty: %s%n", bot.getStockName(), qty);
        
        TossDto.OrderResultDto result = apiClient.createOrder(request);
        if (result == null) {
            throw new RuntimeException("Toss Order response was empty");
        }

        OrderRecord record = new OrderRecord();
        record.setStockCode(result.getSymbol());
        record.setStockName(bot.getStockName());
        record.setOrderType(result.getSide());
        record.setPrice(price.doubleValue()); // 시장가 체결이므로 조회했던 시점의 가격을 매핑 (혹은 실제 체결가)
        record.setQuantity(result.getQuantity().doubleValue());
        
        // 시장가 주문은 대부분 즉시 FILLED 상태가 됨
        String status = result.getStatus() != null ? result.getStatus() : "FILLED";
        record.setStatus(status);
        record.setOrderTime(LocalDateTime.now());
        record.setTradingMode("REAL");

        System.out.printf("[Real Order] BUY ORDER SUBMITTED. ID: %s, Status: %s%n", result.getOrderId(), status);

        return orderRecordRepository.save(record);
    }

    @Override
    @Transactional
    public OrderRecord executeSell(TradingBot bot, BigDecimal price) {
        // 실제 보유 주식 조회
        TossDto.HoldingsOverviewDto holdings = apiClient.getHoldings();
        if (holdings == null || holdings.getItems() == null || holdings.getItems().isEmpty()) {
            System.out.printf("[Real Order] Failed to sell %s: No holdings retrieved from Toss API.%n", bot.getStockName());
            return null;
        }

        // 해당 종목의 보유량 찾기
        TossDto.HoldingsItemDto targetHolding = holdings.getItems().stream()
                .filter(item -> item.getSymbol().equals(bot.getStockCode()))
                .findFirst()
                .orElse(null);

        if (targetHolding == null || targetHolding.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            System.out.printf("[Real Order] Failed to sell %s: Zero holdings on actual account.%n", bot.getStockName());
            return null;
        }

        BigDecimal qty = targetHolding.getQuantity();

        // 전량 시장가 매도 주문 요청 생성
        TossDto.OrderCreateRequest request = new TossDto.OrderCreateRequest(
                UUID.randomUUID().toString(),
                bot.getStockCode(),
                "SELL",
                "MARKET",
                "DAY",
                qty,
                null,
                false
        );

        System.out.printf("[Real Order] Requesting SELL MARKET for %s, Qty: %s%n", bot.getStockName(), qty);

        TossDto.OrderResultDto result = apiClient.createOrder(request);
        if (result == null) {
            throw new RuntimeException("Toss Order response was empty");
        }

        OrderRecord record = new OrderRecord();
        record.setStockCode(result.getSymbol());
        record.setStockName(bot.getStockName());
        record.setOrderType(result.getSide());
        record.setPrice(price.doubleValue());
        record.setQuantity(result.getQuantity().doubleValue());
        
        String status = result.getStatus() != null ? result.getStatus() : "FILLED";
        record.setStatus(status);
        record.setOrderTime(LocalDateTime.now());
        record.setTradingMode("REAL");

        System.out.printf("[Real Order] SELL ORDER SUBMITTED. ID: %s, Status: %s%n", result.getOrderId(), status);

        return orderRecordRepository.save(record);
    }
}
