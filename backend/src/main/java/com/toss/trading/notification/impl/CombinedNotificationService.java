package com.toss.trading.notification.impl;

import com.toss.trading.domain.AssetHistory;
import com.toss.trading.domain.OrderRecord;
import com.toss.trading.repository.AssetHistoryRepository;
import com.toss.trading.repository.OrderRecordRepository;
import com.toss.trading.notification.NotificationService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class CombinedNotificationService implements NotificationService {

    private final JavaMailSender mailSender;
    private final AssetHistoryRepository assetHistoryRepository;
    private final OrderRecordRepository orderRepository;
    private final WebClient webClient;

    private final String telegramBotToken;
    private final String telegramChatId;
    private final String emailRecipient;
    private final String emailSender;
    private final String tradingMode;

    public CombinedNotificationService(
            JavaMailSender mailSender,
            AssetHistoryRepository assetHistoryRepository,
            OrderRecordRepository orderRepository,
            WebClient.Builder webClientBuilder,
            @Value("${trading.telegram.bot-token:}") String telegramBotToken,
            @Value("${trading.telegram.chat-id:}") String telegramChatId,
            @Value("${trading.report.email-recipient:}") String emailRecipient,
            @Value("${spring.mail.username:}") String emailSender,
            @Value("${trading.mode}") String tradingMode) {
        this.mailSender = mailSender;
        this.assetHistoryRepository = assetHistoryRepository;
        this.orderRepository = orderRepository;
        this.webClient = webClientBuilder.baseUrl("https://api.telegram.org").build();
        this.telegramBotToken = telegramBotToken;
        this.telegramChatId = telegramChatId;
        this.emailRecipient = emailRecipient;
        this.emailSender = emailSender;
        this.tradingMode = tradingMode;
    }

    @Override
    public void sendOrderAlert(OrderRecord order) {
        if (telegramBotToken == null || telegramBotToken.isEmpty() || "your_telegram_bot_token_here".equals(telegramBotToken)) {
            System.out.println("[Telegram Alert Skip] Token not configured. Message: " + formatTelegramMessage(order));
            return;
        }

        String messageText = formatTelegramMessage(order);
        try {
            webClient.post()
                    .uri("/bot" + telegramBotToken + "/sendMessage")
                    .bodyValue(Map.of(
                            "chat_id", telegramChatId,
                            "text", messageText,
                            "parse_mode", "HTML"
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                            res -> System.out.println("Telegram alert sent successfully."),
                            err -> System.err.println("Failed to send Telegram alert: " + err.getMessage())
                    );
        } catch (Exception e) {
            System.err.println("Exception sending Telegram message: " + e.getMessage());
        }
    }

    @Override
    public void sendDailyReport() {
        LocalDate today = LocalDate.now();
        AssetHistory todayAsset = assetHistoryRepository.findByRecordDate(today).orElse(null);
        if (todayAsset == null) {
            System.out.println("No asset history logged for today, skipping email report.");
            return;
        }

        if (emailRecipient == null || emailRecipient.isEmpty() || "your_gmail_address@gmail.com".equals(emailRecipient)) {
            System.out.println("[Email Report Skip] Recipient email not configured. Content would be: " + buildDailyReportHtml(todayAsset));
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(emailSender);
            helper.setTo(emailRecipient);
            helper.setSubject("[Toss Auto Trading] " + today.toString() + " 일간 투자 보고서 (" + tradingMode.toUpperCase() + ")");
            helper.setText(buildDailyReportHtml(todayAsset), true);

            mailSender.send(message);
            System.out.println("Daily email report sent successfully to " + emailRecipient);
        } catch (Exception e) {
            System.err.println("Failed to send daily email report: " + e.getMessage());
        }
    }

    @Override
    public void sendTelegramMessage(String message) {
        if (telegramBotToken == null || telegramBotToken.isEmpty() || "your_telegram_bot_token_here".equals(telegramBotToken)) {
            System.out.println("[Telegram Message Skip] Token not configured. Message: " + message);
            return;
        }

        try {
            webClient.post()
                    .uri("/bot" + telegramBotToken + "/sendMessage")
                    .bodyValue(Map.of(
                            "chat_id", telegramChatId,
                            "text", message,
                            "parse_mode", "HTML"
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                            res -> System.out.println("Telegram custom message sent successfully."),
                            err -> System.err.println("Failed to send Telegram custom message: " + err.getMessage())
                    );
        } catch (Exception e) {
            System.err.println("Exception sending Telegram custom message: " + e.getMessage());
        }
    }

    private String formatTelegramMessage(OrderRecord order) {
        String emoji = "BUY".equalsIgnoreCase(order.getOrderType()) ? "🔵 [매수 체결]" : "🔴 [매도 체결]";
        String modeBadge = "MOCK".equalsIgnoreCase(order.getTradingMode()) ? "<b>[MOCK (가상)]</b>" : "<b>[REAL (실전)]</b>";
        
        return String.format(
                "%s %s\n" +
                "<b>종목명:</b> %s (%s)\n" +
                "<b>체결가:</b> %,.0f 원\n" +
                "<b>체결량:</b> %,.2f 주\n" +
                "<b>총금액:</b> %,.0f 원\n" +
                "<b>시간:</b> %s",
                emoji, modeBadge,
                order.getStockName(), order.getStockCode(),
                order.getPrice(),
                order.getQuantity(),
                order.getPrice() * order.getQuantity(),
                order.getOrderTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
    }

    private String buildDailyReportHtml(AssetHistory todayAsset) {
        // 전일 대비 수익률 계산을 위해 전일 자산 데이터 조회
        List<AssetHistory> history = assetHistoryRepository.findAllByOrderByRecordDateAsc();
        double prevTotalAsset = todayAsset.getTotalAsset();
        
        if (history.size() >= 2) {
            // 오늘 데이터 바로 직전의 데이터 획득
            prevTotalAsset = history.get(history.size() - 2).getTotalAsset();
        }

        double dailyProfit = todayAsset.getTotalAsset() - prevTotalAsset;
        double dailyProfitRate = prevTotalAsset > 0 ? (dailyProfit / prevTotalAsset) * 100.0 : 0.0;

        // 오늘 체결된 주문들 가져오기
        LocalDate today = LocalDate.now();
        List<OrderRecord> todayOrders = orderRepository.findAllByOrderByOrderTimeDesc().stream()
                .filter(o -> o.getOrderTime().toLocalDate().isEqual(today))
                .toList();

        StringBuilder ordersHtml = new StringBuilder();
        if (todayOrders.isEmpty()) {
            ordersHtml.append("<tr><td colspan='5' style='text-align:center; padding: 10px; color:#777;'>오늘 체결된 거래가 없습니다.</td></tr>");
        } else {
            for (OrderRecord o : todayOrders) {
                String typeColor = "BUY".equalsIgnoreCase(o.getOrderType()) ? "#2b6cb0" : "#c53030";
                String typeName = "BUY".equalsIgnoreCase(o.getOrderType()) ? "매수" : "매도";
                ordersHtml.append(String.format(
                        "<tr>" +
                        "  <td style='padding: 8px; border-bottom: 1px solid #ddd;'>%s</td>" +
                        "  <td style='padding: 8px; border-bottom: 1px solid #ddd;'>%s (%s)</td>" +
                        "  <td style='padding: 8px; border-bottom: 1px solid #ddd; color:%s; font-weight:bold;'>%s</td>" +
                        "  <td style='padding: 8px; border-bottom: 1px solid #ddd; text-align:right;'>%,.0f원 (%,.2f주)</td>" +
                        "  <td style='padding: 8px; border-bottom: 1px solid #ddd; text-align:right;'>%,.0f원</td>" +
                        "</tr>",
                        o.getOrderTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        o.getStockName(), o.getStockCode(),
                        typeColor, typeName,
                        o.getPrice(), o.getQuantity(),
                        o.getPrice() * o.getQuantity()
                ));
            }
        }

        String profitColor = dailyProfit >= 0 ? "#c53030" : "#2b6cb0";
        String profitSign = dailyProfit >= 0 ? "+" : "";

        return String.format(
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; padding: 20px; border-radius: 8px; background-color: #f7fafc;'>" +
                "  <h2 style='text-align: center; color: #2d3748; margin-top: 0;'>📊 Toss Auto Trading 일간 리포트</h2>" +
                "  <p style='text-align: center; color: #718096; font-size: 14px;'>보고서 작성일: %s | 실행 모드: <b>%s</b></p>" +
                "  <hr style='border: 0; border-top: 1px solid #e2e8f0; margin: 20px 0;'>" +
                "  " +
                "  <h3 style='color: #4a5568;'>1. 자산 현황 요약</h3>" +
                "  <table style='width: 100%%; border-collapse: collapse; background-color: white; border-radius: 6px; overflow: hidden;'>" +
                "    <tr style='background-color: #edf2f7;'>" +
                "      <th style='padding: 10px; text-align: left; border-bottom: 2px solid #cbd5e0;'>항목</th>" +
                "      <th style='padding: 10px; text-align: right; border-bottom: 2px solid #cbd5e0;'>금액 / 변동</th>" +
                "    </tr>" +
                "    <tr>" +
                "      <td style='padding: 10px; border-bottom: 1px solid #e2e8f0; font-weight: bold;'>총 자산 (Total Assets)</td>" +
                "      <td style='padding: 10px; border-bottom: 1px solid #e2e8f0; text-align: right; font-weight: bold; font-size: 16px; color: #1a202c;'>%,.0f 원</td>" +
                "    </tr>" +
                "    <tr>" +
                "      <td style='padding: 10px; border-bottom: 1px solid #e2e8f0;'>예수금 (Cash Balance)</td>" +
                "      <td style='padding: 10px; border-bottom: 1px solid #e2e8f0; text-align: right;'>%,.0f 원</td>" +
                "    </tr>" +
                "    <tr>" +
                "      <td style='padding: 10px; border-bottom: 1px solid #e2e8f0;'>주식 평가액 (Stocks Value)</td>" +
                "      <td style='padding: 10px; border-bottom: 1px solid #e2e8f0; text-align: right;'>%,.0f 원</td>" +
                "    </tr>" +
                "    <tr>" +
                "      <td style='padding: 10px; border-bottom: 1px solid #e2e8f0; font-weight: bold;'>전일비 손익 (Daily PnL)</td>" +
                "      <td style='padding: 10px; border-bottom: 1px solid #e2e8f0; text-align: right; color: %s; font-weight: bold;'>%s%,.0f 원 (%s%,.2f%%)</td>" +
                "    </tr>" +
                "  </table>" +
                "  " +
                "  <h3 style='color: #4a5568; margin-top: 30px;'>2. 오늘 체결 내역</h3>" +
                "  <table style='width: 100%%; border-collapse: collapse; background-color: white; border-radius: 6px; overflow: hidden; font-size: 13px;'>" +
                "    <tr style='background-color: #edf2f7; text-align: left;'>" +
                "      <th style='padding: 8px; border-bottom: 2px solid #cbd5e0;'>시간</th>" +
                "      <th style='padding: 8px; border-bottom: 2px solid #cbd5e0;'>종목</th>" +
                "      <th style='padding: 8px; border-bottom: 2px solid #cbd5e0;'>유형</th>" +
                "      <th style='padding: 8px; border-bottom: 2px solid #cbd5e0; text-align:right;'>단가(수량)</th>" +
                "      <th style='padding: 8px; border-bottom: 2px solid #cbd5e0; text-align:right;'>총액</th>" +
                "    </tr>" +
                "    %s" +
                "  </table>" +
                "  " +
                "  <hr style='border: 0; border-top: 1px solid #e2e8f0; margin: 35px 0 20px 0;'>" +
                "  <p style='font-size: 11px; text-align: center; color: #a0aec0;'>본 메일은 Toss OpenAPI 자동매매 프로그램에서 자동 발송되었습니다.</p>" +
                "</div>",
                today.toString(),
                tradingMode.toUpperCase(),
                todayAsset.getTotalAsset(),
                todayAsset.getCashBalance(),
                todayAsset.getStockEvaluation(),
                profitColor, profitSign, dailyProfit, profitSign, dailyProfitRate,
                ordersHtml.toString()
        );
    }
}
