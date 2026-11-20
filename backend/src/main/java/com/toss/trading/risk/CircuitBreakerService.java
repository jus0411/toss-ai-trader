package com.toss.trading.risk;

import com.toss.trading.domain.OrderRecord;
import com.toss.trading.domain.TradingBot;
import com.toss.trading.repository.OrderRecordRepository;
import com.toss.trading.repository.TradingBotRepository;
import com.toss.trading.email.EmailService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CircuitBreakerService {

    private final TradingBotRepository botRepository;
    private final OrderRecordRepository orderRepository;
    private final EmailService emailService;

    public CircuitBreakerService(
            TradingBotRepository botRepository,
            OrderRecordRepository orderRepository,
            EmailService emailService) {
        this.botRepository = botRepository;
        this.orderRepository = orderRepository;
        this.emailService = emailService;
    }

    /**
     * 특정 봇의 실시간 주가 급락 여부를 판단하고, 조건 충족 시 봇 가동을 즉시 차단(Circuit Breaker)합니다.
     */
    public boolean checkAndTrigger(TradingBot bot, BigDecimal currentPrice) {
        if (!bot.isActive()) {
            return false;
        }

        try {
            // 해당 봇의 평단가 가져오기
            List<OrderRecord> symbolOrders = orderRepository.findAllByOrderByOrderTimeDesc().stream()
                    .filter(o -> o.getStockCode().equals(bot.getStockCode()) && "FILLED".equalsIgnoreCase(o.getStatus()))
                    .toList();

            double totalQty = 0.0;
            double totalCost = 0.0;
            double avgPrice = 0.0;

            for (int i = symbolOrders.size() - 1; i >= 0; i--) {
                OrderRecord o = symbolOrders.get(i);
                if ("BUY".equalsIgnoreCase(o.getOrderType())) {
                    totalQty += o.getQuantity();
                    totalCost += o.getQuantity() * o.getPrice();
                    avgPrice = totalQty > 0 ? totalCost / totalQty : 0.0;
                } else if ("SELL".equalsIgnoreCase(o.getOrderType())) {
                    totalQty = Math.max(0.0, totalQty - o.getQuantity());
                    totalCost = totalQty * avgPrice;
                    if (totalQty == 0.0) {
                        avgPrice = 0.0;
                        totalCost = 0.0;
                    }
                }
            }

            if (totalQty <= 0 || avgPrice <= 0) {
                return false; // 보유 물량이 없으면 급락 차단 대상에서 제외
            }

            // 급락 하락률 기준 설정 (예: 평단가 대비 -5% 이하 하락 시)
            double dropPercent = bot.getStopLossPercent() > 0 ? bot.getStopLossPercent() : 5.0; 
            BigDecimal averagePrice = BigDecimal.valueOf(avgPrice);
            BigDecimal limitRatio = BigDecimal.valueOf(1.0 - (dropPercent / 100.0));
            BigDecimal triggerPrice = averagePrice.multiply(limitRatio);

            System.out.printf("[CircuitBreaker] %s: Current=%s, AvgPrice=%s, TriggerPrice=%s (Limit=%s%%)%n",
                    bot.getStockCode(), currentPrice, averagePrice, triggerPrice, dropPercent);

            if (currentPrice.compareTo(triggerPrice) <= 0) {
                // 서킷 브레이커 발동! 봇 일시정지
                bot.setActive(false);
                bot.setStatusMessage(String.format("서킷브레이커 발동 정지 (평단 %s 대비 %s%% 초과 하락)", averagePrice, dropPercent));
                botRepository.save(bot);

                // SOS 이메일 전송
                sendSosEmail(bot, averagePrice, currentPrice, dropPercent);
                return true;
            }

        } catch (Exception e) {
            System.err.println("Error evaluating Circuit Breaker for " + bot.getStockCode() + ": " + e.getMessage());
        }

        return false;
    }

    private void sendSosEmail(TradingBot bot, BigDecimal avgPrice, BigDecimal currentPrice, double dropPercent) {
        String title = "🚨 [비상 경보] 주가 급락에 따른 자동매매 긴급 정지 (Circuit Breaker)";
        String subtitle = String.format("감시 종목 %s (%s) 봇의 작동이 일시 정지되었습니다.", bot.getStockName(), bot.getStockCode());

        StringBuilder html = new StringBuilder();
        html.append("<div class='card'>");
        html.append("<h3 class='card-title' style='color: #ef4444;'>위험 관리 긴급 알림</h3>");
        html.append(String.format("<p>보유 중인 <strong>%s (%s)</strong> 종목의 실시간 주가가 주주님의 평단가 대비 설정한 손실 한도인 <span class='text-red'>%s%%</span>를 초과 하락하여 시스템 안전을 위해 자동매매를 즉시 정지(Circuit Breaker) 조치하였습니다.</p>",
                bot.getStockName(), bot.getStockCode(), dropPercent));
        html.append("<ul>");
        html.append(String.format("<li><strong>주주님 평단가:</strong> ₩%,.0f원</li>", avgPrice.doubleValue()));
        html.append(String.format("<li><strong>서킷브레이커 작동시가:</strong> ₩%,.0f원</li>", currentPrice.doubleValue()));
        html.append(String.format("<li><strong>실시간 하락폭:</strong> <span class='text-red'>-%,.2f%%</span></li>", 
                (avgPrice.subtract(currentPrice)).doubleValue() / avgPrice.doubleValue() * 100.0));
        html.append("</ul>");
        html.append("<p style='background-color: #7f1d1d; color: #fca5a5; padding: 10px; border-radius: 6px; font-size: 13px; font-weight: bold;'>");
        html.append("※ 이후의 추가적인 폭락 손실로부터 자산을 보호하기 위해, 해당 종목 봇은 비활성화 상태로 변경되었습니다. 웹 대시보드에서 차트와 시장 상황을 파악하신 후 수동으로 봇 재개 혹은 익/손절 정리를 판단해주시기 바랍니다.");
        html.append("</p>");
        html.append("</div>");

        String content = emailService.buildTemplate(title, subtitle, html.toString());
        emailService.sendHtmlEmail(title, content);
    }
}
