package com.toss.trading.toss.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

public class TossDto {

    // === 계좌 목록 조회 API ===
    @Data
    public static class AccountListResponse {
        private List<AccountDto> result;
    }

    @Data
    public static class AccountDto {
        private String accountNo;
        private Long accountSeq;
        private String accountType;
    }

    // === 현재가 조회 API ===
    @Data
    public static class PriceListResponse {
        private List<PriceDto> result;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceDto {
        private String symbol;
        private String timestamp;
        private BigDecimal lastPrice;
        private String currency;
    }

    // === 보유 주식 조회 API ===
    @Data
    public static class HoldingsResponse {
        private HoldingsOverviewDto result;
    }

    @Data
    public static class HoldingsOverviewDto {
        private AmountDto totalPurchaseAmount;
        private OverviewMarketValueDto marketValue;
        private OverviewProfitLossDto profitLoss;
        private List<HoldingsItemDto> items;
    }

    @Data
    public static class AmountDto {
        private BigDecimal amount;
        private String currency;
    }

    @Data
    public static class OverviewMarketValueDto {
        private BigDecimal amount;
        private String currency;
    }

    @Data
    public static class OverviewProfitLossDto {
        private BigDecimal amount;
        private String currency;
        private BigDecimal rate; // 수익률 (%)
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoldingsItemDto {
        private String symbol;
        private String name;
        private String marketCountry;
        private String currency;
        private BigDecimal quantity;
        private BigDecimal lastPrice;
        private BigDecimal averagePurchasePrice;
    }

    // === 주문 생성 API ===
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCreateRequest {
        private String clientOrderId; // 멱등성 키
        private String symbol;
        private String side; // BUY, SELL
        private String orderType; // LIMIT, MARKET
        private String timeInForce = "DAY";
        private BigDecimal quantity;
        private BigDecimal price; // LIMIT 일 때 필수
        private boolean confirmHighValueOrder = false;
    }

    @Data
    public static class OrderResponse {
        private OrderResultDto result;
    }

    @Data
    public static class OrderResultDto {
        private String orderId;
        private String clientOrderId;
        private String symbol;
        private String side;
        private String orderType;
        private BigDecimal quantity;
        private BigDecimal price;
        private String status; // ORDERED, FILLED, CANCELLED
    }

    // === 캔들 조회 API ===
    @Data
    public static class CandleListResponse {
        private CandlePageResponse result;
    }

    @Data
    public static class CandlePageResponse {
        private List<CandleDto> candles;
        private String nextBefore;
    }

    @Data
    public static class CandleDto {
        private String timestamp;
        private BigDecimal openPrice;
        private BigDecimal highPrice;
        private BigDecimal lowPrice;
        private BigDecimal closePrice;
        private BigDecimal volume;
        private String currency;

        // Backward compatibility overrides
        public BigDecimal getOpen() { return openPrice; }
        public BigDecimal getHigh() { return highPrice; }
        public BigDecimal getLow() { return lowPrice; }
        public BigDecimal getClose() { return closePrice; }
        
        public void setOpen(BigDecimal open) { this.openPrice = open; }
        public void setHigh(BigDecimal high) { this.highPrice = high; }
        public void setLow(BigDecimal low) { this.lowPrice = low; }
        public void setClose(BigDecimal close) { this.closePrice = close; }
    }
}
