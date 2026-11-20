package com.toss.trading.risk;

import com.toss.trading.domain.AssetHistory;
import com.toss.trading.domain.OrderRecord;
import com.toss.trading.domain.TradingBot;
import com.toss.trading.repository.AssetHistoryRepository;
import com.toss.trading.repository.TradingBotRepository;
import com.toss.trading.toss.TossApiClient;
import com.toss.trading.toss.TossOrderService;
import com.toss.trading.toss.dto.TossDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class AccountKillSwitchService {

    private final TradingBotRepository botRepository;
    private final AssetHistoryRepository assetHistoryRepository;
    private final TossOrderService orderService;
    private final TossApiClient apiClient;

    // 계좌 단위 최대 낙폭 한계선 (MDD: 12% 하락 시 셧다운)
    private static final double MDD_LIMIT_PERCENT = 12.0;
    // 전일 대비 최대 손실 한계선 (전일자산 대비 7% 하락 시 셧다운)
    private static final double DAILY_LOSS_LIMIT_PERCENT = 7.0;

    public AccountKillSwitchService(TradingBotRepository botRepository,
                                     AssetHistoryRepository assetHistoryRepository,
                                     TossOrderService orderService,
                                     TossApiClient apiClient) {
        this.botRepository = botRepository;
        this.assetHistoryRepository = assetHistoryRepository;
        this.orderService = orderService;
        this.apiClient = apiClient;
    }

    /**
     * 전체 자산의 손실률 및 최대 낙폭을 계산하여 비상 셧다운을 실행합니다.
     * 위험 탐지 시 모든 활성 봇을 정지시키고 보유 중인 주식을 전량 청산 매도합니다.
     */
    @Transactional
    public boolean checkAndTriggerShutdown() {
        List<AssetHistory> history = assetHistoryRepository.findAllByOrderByRecordTimeAsc();
        if (history.isEmpty()) {
            return false;
        }

        AssetHistory latest = history.get(history.size() - 1);
        double currentTotalAsset = latest.getTotalAsset();

        // 1. 역대 최고 자산(Peak) 조회하여 MDD 계산
        double peakAsset = currentTotalAsset;
        for (AssetHistory h : history) {
            if (h.getTotalAsset() > peakAsset) {
                peakAsset = h.getTotalAsset();
            }
        }

        double mdd = peakAsset > 0 ? ((peakAsset - currentTotalAsset) / peakAsset) * 100.0 : 0.0;

        // 2. 전일 자산 대비 손실률 계산
        double dailyLoss = 0.0;
        if (history.size() >= 2) {
            AssetHistory yesterday = history.get(history.size() - 2);
            double yesterdayTotal = yesterday.getTotalAsset();
            dailyLoss = yesterdayTotal > 0 ? ((yesterdayTotal - currentTotalAsset) / yesterdayTotal) * 100.0 : 0.0;
        }

        // 3. 임계치 초과 여부 검사
        boolean triggerMdd = mdd >= MDD_LIMIT_PERCENT;
        boolean triggerDaily = dailyLoss >= DAILY_LOSS_LIMIT_PERCENT;

        if (triggerMdd || triggerDaily) {
            String reason = triggerMdd 
                ? String.format("계좌 최대 낙폭(MDD) 임계치 초과 (현재: %.1f%% / 한계: %.1f%%)", mdd, MDD_LIMIT_PERCENT)
                : String.format("전일 대비 당일 누적 손실 한계 초과 (현재: %.1f%% / 한계: %.1f%%)", dailyLoss, DAILY_LOSS_LIMIT_PERCENT);
            
            System.err.println("🚨🚨🚨 [SAFETY SYSTEM] ACCOUNT KILL SWITCH TRIGGERED: " + reason);
            executeEmergencyLiquidation(reason);
            return true;
        }

        return false;
    }

    /**
     * 모든 감시 봇을 비활성화하고 보유 자산을 즉각 시장가 일괄 매도 전량 청산합니다.
     */
    private void executeEmergencyLiquidation(String reason) {
        // 1. 모든 가동 중인 봇 일괄 비활성화 및 상태 메시지 기입
        List<TradingBot> activeBots = botRepository.findByActive(true);
        for (TradingBot bot : activeBots) {
            bot.setActive(false);
            bot.setStatusMessage("🚨 긴급 셧다운 강제 종료: " + reason);
            botRepository.save(bot);
        }

        // 2. 보유한 모든 주식을 강제 일괄 청산 매도
        // MockOrderService 등은 executeSell 시 해당 봇의 종목코드 보유 잔고를 전량 매도함.
        for (TradingBot bot : activeBots) {
            if ("PORTFOLIO".equals(bot.getStockCode())) {
                continue; // 자산 배분 전략은 개별 종목 매도로 해소
            }
            List<TossDto.CandleDto> candles = apiClient.getCandles(bot.getStockCode(), "1d", 1);
            BigDecimal currentPrice = candles.isEmpty() ? BigDecimal.valueOf(50000) : candles.get(0).getClose();
            
            System.out.printf("[Emergency Liquidation] Selling %s (%s) due to system shutdown.%n", bot.getStockName(), bot.getStockCode());
            orderService.executeSell(bot, currentPrice);
        }
    }
}
