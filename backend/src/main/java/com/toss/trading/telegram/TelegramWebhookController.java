package com.toss.trading.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telegram")
public class TelegramWebhookController {

    private final TelegramBotCommandService telegramBotCommandService;
    private final ObjectMapper objectMapper;

    public TelegramWebhookController(TelegramBotCommandService telegramBotCommandService, ObjectMapper objectMapper) {
        this.telegramBotCommandService = telegramBotCommandService;
        this.objectMapper = objectMapper;
    }

    /**
     * 텔레그램 봇 Webhook 메시지를 수신하는 엔드포인트
     */
    @PostMapping("/webhook")
    public String receiveWebhook(@RequestBody String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode messageNode = root.path("message");
            if (!messageNode.isMissingNode()) {
                String text = messageNode.path("text").asText();
                String chatId = messageNode.path("chat").path("id").asText();
                
                System.out.printf("[Telegram Webhook] Received message: '%s' from ChatID: %s%n", text, chatId);
                telegramBotCommandService.processTelegramMessage(text, chatId);
            }
        } catch (Exception e) {
            System.err.println("[Telegram Webhook] Payload parse error: " + e.getMessage());
        }
        return "OK";
    }
}
