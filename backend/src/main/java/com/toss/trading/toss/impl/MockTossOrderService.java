package com.toss.trading.toss.impl;

import com.toss.trading.domain.AssetHistory;
import com.toss.trading.domain.OrderRecord;
import com.toss.trading.domain.TradingBot;
import com.toss.trading.repository.AssetHistoryRepository;
import com.toss.trading.repository.OrderRecordRepository;
import com.toss.trading.toss.TossOrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service("mockTossOrderService")
public class MockTossOrderService implements TossOrderService {

    private final AssetHistoryRepository assetHistoryRepository;
    private final OrderRecordRepository orderRecordRepository;
    private final ObjectMapper objectMapper;
    private static final BigDecimal INITIAL_CASH = BigDecimal.valueOf(10_000_000); // 가상 계좌 초기 자금 1,000만원

    public MockTossOrderService(
            AssetHistoryRepository assetHistoryRepository,
            OrderRecordRepository orderRecordRepository,
            ObjectMapper objectMapper) {
        this.assetHistoryRepository = assetHistoryRepository;
        this.orderRecordRepository = orderRecordRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 현재 가상 자산 상태를 가져오거나 없으면 초기화하여 반환합니다.
     */
    private AssetHistory getOrCreateCurrentAsset() {
        return assetHistoryRepository.findFirstByOrderByRecordTimeDesc()
                .orElseGet(() -> {
                    AssetHistory initial = new AssetHistory();
                    initial.setRecordDate(LocalDate.now());
                    initial.setRecordTime(LocalDateTime.now());
                    initial.setCashBalance(INITIAL_CASH.doubleValue());
                    initial.setStockEvaluation(0.0);
                    initial.setTotalAsset(INITIAL_CASH.doubleValue());
                    return assetHistoryRepository.save(initial);
                });
    }

    @Override
    @Transactional
    public synchronized OrderRecord executeBuy(TradingBot bot, BigDecimal price, BigDecimal amount) {
        // 1. 국내 주식은 소수점 매수가 안 되므로 정수 주 수량 계산 (수량 = 투자금액 / 주가)
        BigDecimal qtyDecimal = amount.divide(price, 0, RoundingMode.DOWN);
        if (qtyDecimal.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.printf("[Mock Order] Failed to buy %s: Amount %s is too low for share price %s%n",
                    bot.getStockCode(), amount, price);
            return null;
        }

        BigDecimal cost = price.multiply(qtyDecimal);
        AssetHistory currentAsset = getOrCreateCurrentAsset();
        BigDecimal cash = BigDecimal.valueOf(currentAsset.getCashBalance());

        // 예수금 잔고 확인
        if (cash.compareTo(cost) < 0) {
            System.out.printf("[Mock Order] Insufficient virtual cash. Cash: %s, Cost: %s%n", cash, cost);
            return null;
        }

        // 가상 자산 차감 및 갱신 (새로운 타임스탬프 레코드 삽입)
        BigDecimal remainingCash = cash.subtract(cost);
        AssetHistory nextAsset = new AssetHistory();
        nextAsset.setRecordDate(LocalDate.now());
        nextAsset.setRecordTime(LocalDateTime.now());
        nextAsset.setCashBalance(remainingCash.doubleValue());
        nextAsset.setStockEvaluation(currentAsset.getStockEvaluation());
        nextAsset.setTotalAsset(remainingCash.doubleValue() + currentAsset.getStockEvaluation());
        assetHistoryRepository.save(nextAsset);

        // 주문 기록 저장
        OrderRecord record = new OrderRecord();
        record.setStockCode(bot.getStockCode());
        record.setStockName(bot.getStockName());
        record.setOrderType("BUY");
        record.setPrice(price.doubleValue());
        record.setQuantity(qtyDecimal.doubleValue());
        record.setStatus("FILLED");
        record.setOrderTime(LocalDateTime.now());
        record.setTradingMode("MOCK");

        System.out.printf("[Mock Order] BUY FILLED. Stock: %s, Price: %s, Qty: %s, Total: %s%n",
                bot.getStockName(), price, qtyDecimal, cost);

        return orderRecordRepository.save(record);
    }

    @Override
    @Transactional
    public synchronized OrderRecord executeSell(TradingBot bot, BigDecimal price) {
        // 이 봇(종목코드)으로 체결된 가상 매매 전체 내역을 가져와 현재 가상 보유 수량을 파악
        List<OrderRecord> orders = orderRecordRepository.findAllByOrderByOrderTimeDesc();
        BigDecimal boughtQty = orders.stream()
                .filter(o -> o.getStockCode().equals(bot.getStockCode()) && "BUY".equalsIgnoreCase(o.getOrderType()) && "FILLED".equalsIgnoreCase(o.getStatus()))
                .map(o -> BigDecimal.valueOf(o.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal soldQty = orders.stream()
                .filter(o -> o.getStockCode().equals(bot.getStockCode()) && "SELL".equalsIgnoreCase(o.getOrderType()) && "FILLED".equalsIgnoreCase(o.getStatus()))
                .map(o -> BigDecimal.valueOf(o.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal holdingsQty = boughtQty.subtract(soldQty);

        if (holdingsQty.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.printf("[Mock Order] Failed to sell %s: No virtual holdings available.%n", bot.getStockName());
            return null;
        }

        BigDecimal sellQty = holdingsQty;
        if (bot.getParameters() != null && !bot.getParameters().trim().isEmpty()) {
            try {
                JsonNode paramsNode = objectMapper.readTree(bot.getParameters());
                if (paramsNode.has("manualSellQty")) {
                    BigDecimal manualSellQty = BigDecimal.valueOf(paramsNode.get("manualSellQty").asDouble());
                    if (manualSellQty.compareTo(BigDecimal.ZERO) > 0 && manualSellQty.compareTo(holdingsQty) <= 0) {
                        sellQty = manualSellQty;
                    }
                }
            } catch (Exception e) {
                // 기본 전량 매도로 후행 처리
            }
        }

        BigDecimal revenue = price.multiply(sellQty);
        AssetHistory currentAsset = getOrCreateCurrentAsset();
        BigDecimal cash = BigDecimal.valueOf(currentAsset.getCashBalance());

        // 가상 예수금 증액 및 신규 레코드 삽입
        BigDecimal newCash = cash.add(revenue);
        AssetHistory nextAsset = new AssetHistory();
        nextAsset.setRecordDate(LocalDate.now());
        nextAsset.setRecordTime(LocalDateTime.now());
        nextAsset.setCashBalance(newCash.doubleValue());
        nextAsset.setStockEvaluation(currentAsset.getStockEvaluation() - (sellQty.doubleValue() * price.doubleValue()));
        nextAsset.setStockEvaluation(Math.max(0.0, nextAsset.getStockEvaluation())); // 0 이하 방지
        nextAsset.setTotalAsset(newCash.doubleValue() + nextAsset.getStockEvaluation());
        assetHistoryRepository.save(nextAsset);

        // 주문 기록 저장
        OrderRecord record = new OrderRecord();
        record.setStockCode(bot.getStockCode());
        record.setStockName(bot.getStockName());
        record.setOrderType("SELL");
        record.setPrice(price.doubleValue());
        record.setQuantity(sellQty.doubleValue());
        record.setStatus("FILLED");
        record.setOrderTime(LocalDateTime.now());
        record.setTradingMode("MOCK");

        System.out.printf("[Mock Order] SELL FILLED. Stock: %s, Price: %s, Qty: %s, Revenue: %s%n",
                bot.getStockName(), price, sellQty, revenue);

        return orderRecordRepository.save(record);
    }
}
