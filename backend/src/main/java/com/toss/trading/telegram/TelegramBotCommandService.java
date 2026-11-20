package com.toss.trading.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toss.trading.domain.AssetHistory;
import com.toss.trading.domain.TradingBot;
import com.toss.trading.repository.AssetHistoryRepository;
import com.toss.trading.repository.TradingBotRepository;
import com.toss.trading.toss.TossApiClient;
import com.toss.trading.toss.TossOrderService;
import com.toss.trading.toss.dto.TossDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TelegramBotCommandService {

    private final TradingBotRepository botRepository;
    private final AssetHistoryRepository assetHistoryRepository;
    private final TossOrderService orderService;
    private final TossApiClient apiClient;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${trading.telegram.bot-token:}")
    private String botToken;

    @Value("${trading.telegram.chat-id:}")
    private String defaultChatId;

    public TelegramBotCommandService(TradingBotRepository botRepository,
                                     AssetHistoryRepository assetHistoryRepository,
                                     TossOrderService orderService,
                                     TossApiClient apiClient,
                                     ObjectMapper objectMapper) {
        this.botRepository = botRepository;
        this.assetHistoryRepository = assetHistoryRepository;
        this.orderService = orderService;
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().baseUrl("https://api.telegram.org").build();
    }

    /**
     * 수신된 텔레그램 명령어를 분석하여 로직을 격발하고 사용자에게 메시지를 회신합니다.
     */
    public void processTelegramMessage(String text, String chatId) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        String targetChatId = (chatId != null) ? chatId : defaultChatId;
        String rawText = text.trim();

        if (rawText.startsWith("/status")) {
            handleStatus(targetChatId);
        } else if (rawText.startsWith("/stop")) {
            handleStop(rawText, targetChatId);
        } else if (rawText.startsWith("/sellall")) {
            handleSellAll(targetChatId);
        } else if (rawText.startsWith("/help")) {
            handleHelp(targetChatId);
        } else {
            sendResponse(targetChatId, "❓ 알 수 없는 명령어입니다.\n사용 가능한 명령어:\n/status - 자산 및 봇 모니터링\n/stop {코드} - 특정 봇 정지\n/sellall - 전체 주식 긴급 청산\n/help - 가이드라인");
        }
    }

    private void handleStatus(String chatId) {
        List<AssetHistory> history = assetHistoryRepository.findAllByOrderByRecordDateAsc();
        AssetHistory latest = history.isEmpty() ? null : history.get(history.size() - 1);

        StringBuilder sb = new StringBuilder();
        sb.append("📊 [TOSS AUTO TRADER 실시간 상태 리포트]\n\n");
        if (latest != null) {
            sb.append(String.format("💵 가용 예수금: ₩%,.0f원\n", latest.getCashBalance()));
            sb.append(String.format("📈 주식 평가액: ₩%,.0f원\n", latest.getStockEvaluation()));
            sb.append(String.format("💰 누적 총 자산: ₩%,.0f원\n\n", latest.getTotalAsset()));
        } else {
            sb.append("자산 데이터가 아직 집계되지 않았습니다.\n\n");
        }

        List<TradingBot> bots = botRepository.findAll();
        sb.append(String.format("🤖 감시 중인 매매 봇 현황 (%d개):\n", bots.size()));
        for (TradingBot bot : bots) {
            String activeSymbol = bot.isActive() ? "🟢 가동 중" : "⚪ 정지됨";
            sb.append(String.format("- %s (%s) [%s]\n  ┗ %s\n", 
                    bot.getStockName(), bot.getStockCode(), activeSymbol, 
                    bot.isActive() && bot.getStatusMessage() != null ? bot.getStatusMessage() : "감시 대기 중"));
        }

        sendResponse(chatId, sb.toString());
    }

    private void handleStop(String rawText, String chatId) {
        String[] parts = rawText.split("\\s+");
        if (parts.length < 2) {
            sendResponse(chatId, "⚠️ 올바른 형식이 아닙니다.\n예: `/stop 005930` 형태로 종목코드를 입력하세요.");
            return;
        }

        String symbol = parts[1].trim();
        List<TradingBot> bots = botRepository.findAll();
        TradingBot targetBot = null;
        for (TradingBot b : bots) {
            if (symbol.equals(b.getStockCode())) {
                targetBot = b;
                break;
            }
        }

        if (targetBot == null) {
            sendResponse(chatId, String.format("❌ 종목코드 %s에 매칭되는 감시 봇을 찾을 수 없습니다.", symbol));
            return;
        }

        targetBot.setActive(false);
        targetBot.setStatusMessage("정지됨 / 텔레그램 명령에 의한 정지");
        botRepository.save(targetBot);

        sendResponse(chatId, String.format("🛑 [봇 정지 완료] %s (%s)의 자동매매 감시를 중단시켰습니다.", targetBot.getStockName(), symbol));
    }

    private void handleSellAll(String chatId) {
        List<TradingBot> activeBots = botRepository.findByActive(true);
        if (activeBots.isEmpty()) {
            sendResponse(chatId, "⚪ 현재 가동 중인 자동매매 봇이 없어 청산할 주식이 없습니다.");
            return;
        }

        // 1. 모든 봇 정지
        for (TradingBot bot : activeBots) {
            bot.setActive(false);
            bot.setStatusMessage("정지됨 / 텔레그램 긴급 청산 명령");
            botRepository.save(bot);
        }

        // 2. 보유한 가상 주식 강제 전량 매도
        int count = 0;
        for (TradingBot bot : activeBots) {
            if ("PORTFOLIO".equals(bot.getStockCode())) {
                continue;
            }
            List<TossDto.CandleDto> candles = apiClient.getCandles(bot.getStockCode(), "1d", 1);
            BigDecimal currentPrice = candles.isEmpty() ? BigDecimal.valueOf(60000) : candles.get(0).getClose();
            orderService.executeSell(bot, currentPrice);
            count++;
        }

        sendResponse(chatId, String.format("🚨 [긴급 셧다운 청산] 모든 활성 봇(%d개)을 정지시키고 보유 주식을 일괄 시장가 매도 청산 처리했습니다.", count));
    }

    private void handleHelp(String chatId) {
        String helpMsg = "🛡️ [TOSS AUTO TRADER 명령어 목록]\n\n" +
                "/status - 현재 계좌 잔고 정보 및 봇 감시 정황 실시간 모니터링\n" +
                "/stop {종목코드} - 특정 종목의 자동매매 봇 원격 비활성화\n" +
                "/sellall - 가동 중인 봇 일제 정지 및 보유 전 주식 시장가 일괄 강제 청산\n" +
                "/help - 사용법 도움말 호출";
        sendResponse(chatId, helpMsg);
    }

    /**
     * 텔레그램 API 서버로 답장 메시지를 전송합니다.
     */
    public void sendResponse(String chatId, String message) {
        if (botToken == null || botToken.trim().isEmpty() || "your_telegram_bot_token_here".equals(botToken)) {
            System.out.println("[Telegram Mock Send] ChatID: " + chatId + ", Message: " + message);
            return;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", message);

            webClient.post()
                    .uri(String.format("/bot%s/sendMessage", botToken))
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .subscribe(
                            res -> System.out.println("Telegram command response successfully sent."),
                            err -> System.err.println("Failed to send telegram message: " + err.getMessage())
                    );
        } catch (Exception e) {
            System.err.println("Telegram send error: " + e.getMessage());
        }
    }
}
