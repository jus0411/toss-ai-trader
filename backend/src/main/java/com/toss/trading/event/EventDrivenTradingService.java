package com.toss.trading.event;

import com.toss.trading.domain.AssetHistory;
import com.toss.trading.domain.OrderRecord;
import com.toss.trading.domain.TradingBot;
import com.toss.trading.repository.AssetHistoryRepository;
import com.toss.trading.repository.TradingBotRepository;
import com.toss.trading.toss.TossApiClient;
import com.toss.trading.toss.TossOrderService;
import com.toss.trading.toss.dto.TossDto;
import com.toss.trading.ai.NewsSearchService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EventDrivenTradingService {

    private final TradingBotRepository botRepository;
    private final AssetHistoryRepository assetHistoryRepository;
    private final TossOrderService orderService;
    private final TossApiClient apiClient;
    private final NewsSearchService newsSearchService;

    // 강력 호재 키워드 목록 (공시 및 속보성)
    private static final String[] TRIGGER_KEYWORDS = {
            "어닝 서프라이즈", "영업이익 급증", "최대 실적", "특허 취득", "공급 계약 체결", "대규모 수주", "M&A 체결"
    };

    // 종목별 당일 호재 추격 매수 중복 작동 방지용 캐시
    private final Map<String, LocalDate> lastTriggeredCache = new ConcurrentHashMap<>();

    public EventDrivenTradingService(TradingBotRepository botRepository,
                                     AssetHistoryRepository assetHistoryRepository,
                                     TossOrderService orderService,
                                     TossApiClient apiClient,
                                     NewsSearchService newsSearchService) {
        this.botRepository = botRepository;
        this.assetHistoryRepository = assetHistoryRepository;
        this.orderService = orderService;
        this.apiClient = apiClient;
        this.newsSearchService = newsSearchService;
    }

    /**
     * 30초마다 활성 봇들의 종목 관련 뉴스 RSS를 크롤링하여 
     * 기습 실적 호재/공시가 출현했을 경우, 예수금 비중의 10%를 원터치 추격 매수 처리합니다.
     */
    public void scanAndTriggerEventTrading() {
        List<TradingBot> activeBots = botRepository.findByActive(true);
        LocalDate today = LocalDate.now();

        for (TradingBot bot : activeBots) {
            if ("PORTFOLIO".equals(bot.getStockCode())) {
                continue; // 리밸런싱 포트폴리오는 제외
            }

            // 오늘 이미 해당 종목에 대해 호재 추격 매수를 단행했다면 패스
            if (today.equals(lastTriggeredCache.get(bot.getStockCode()))) {
                continue;
            }

            // 1. 해당 종목의 최신 구글 뉴스 피드 수집
            List<Map<String, String>> newsList = newsSearchService.searchStockNews(bot.getStockName());
            if (newsList.isEmpty()) {
                continue;
            }

            // 2. 뉴스 헤드라인 키워드 검사
            boolean keywordMatched = false;
            String matchedHeadline = "";
            for (Map<String, String> news : newsList) {
                String title = news.getOrDefault("title", "");
                for (String kw : TRIGGER_KEYWORDS) {
                    if (title.contains(kw)) {
                        keywordMatched = true;
                        matchedHeadline = title;
                        break;
                    }
                }
                if (keywordMatched) {
                    break;
                }
            }

            // 3. 매칭 성공 시 즉각 추격 매수 (Event-Driven)
            if (keywordMatched) {
                System.out.printf("🎯 [EVENT-DRIVEN TRIGGER] 호재 공시 포착! 종목: %s, 뉴스: %s%n", bot.getStockName(), matchedHeadline);
                
                // 가용 가상 예수금 확인하여 10% 비중 산출
                double cash = 10000000.0;
                Optional<AssetHistory> latestAsset = assetHistoryRepository.findFirstByOrderByRecordTimeDesc();
                if (latestAsset.isPresent()) {
                    cash = latestAsset.get().getCashBalance();
                }

                // 호재 돌파 매매: 가용 예수금의 10% 투입
                double targetBuyAmount = cash * 0.10;
                if (targetBuyAmount >= 10000) {
                    List<TossDto.CandleDto> candles = apiClient.getCandles(bot.getStockCode(), "1d", 1);
                    BigDecimal price = candles.isEmpty() ? BigDecimal.valueOf(50000) : candles.get(0).getClose();

                    // 수동 봇으로 우회하여 1회 추격 매입 격발
                    TradingBot eventBot = new TradingBot();
                    eventBot.setStockCode(bot.getStockCode());
                    eventBot.setStockName(bot.getStockName());
                    eventBot.setAlgorithmType("EVENT_DRIVEN");
                    eventBot.setActive(true);
                    eventBot.setParameters(String.format("{\"eventHeadline\": \"%s\"}", matchedHeadline));

                    try {
                        OrderRecord filledOrder = orderService.executeBuy(eventBot, price, BigDecimal.valueOf(targetBuyAmount));
                        if (filledOrder != null) {
                            lastTriggeredCache.put(bot.getStockCode(), today);
                            bot.setStatusMessage("🎯 공시 호재 발생 추격매수 체결 완료: " + matchedHeadline);
                            botRepository.save(bot);
                            System.out.printf("[Event Trade Success] Purchased %s due to earnings/event success.%n", bot.getStockName());
                        }
                    } catch (Exception e) {
                        System.err.println("Event driven trading order failed: " + e.getMessage());
                    }
                }
            }
        }
    }
}
