package com.toss.trading.email;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    
    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${trading.report.email-recipient:}")
    private String toEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * 지정된 수신자에게 미려한 디자인의 HTML 이메일을 발송합니다.
     */
    public void sendHtmlEmail(String subject, String htmlContent) {
        if (toEmail == null || toEmail.trim().isEmpty() || toEmail.contains("your_gmail_address")) {
            System.out.println("=================================================");
            System.out.println(" [MOCK EMAIL SERVICE] Recipient not configured.");
            System.out.println(" Subject: " + subject);
            System.out.println(" Content (Abridged):\n" + htmlContent.substring(0, Math.min(htmlContent.length(), 500)) + "...");
            System.out.println("=================================================");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail != null && !fromEmail.isEmpty() ? fromEmail : "jus04116@gmail.com");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            System.out.println("Successfully sent HTML email to: " + toEmail + " | Subject: " + subject);
        } catch (Exception e) {
            System.err.println("Failed to send email via SMTP: " + e.getMessage());
            System.out.println("=================================================");
            System.out.println(" [SMTP FALLBACK LOG] Subject: " + subject);
            System.out.println(" Content:\n" + htmlContent);
            System.out.println("=================================================");
        }
    }

    /**
     * 메일 레이아웃 래퍼: 미려한 다크 테마 프레임을 씌워줍니다.
     */
    public String buildTemplate(String title, String subtitle, String content) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "  <meta charset='UTF-8'>" +
                "  <style>" +
                "    body { font-family: 'Malgun Gothic', 'Apple SD Gothic Neo', sans-serif; background-color: #0f172a; color: #e2e8f0; margin: 0; padding: 20px; }" +
                "    .container { max-width: 600px; margin: 0 auto; background-color: #1e293b; border-radius: 12px; border: 1px solid #334155; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); }" +
                "    .header { background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%); padding: 30px; text-align: center; color: #ffffff; }" +
                "    .header h1 { margin: 0; font-size: 24px; font-weight: 700; letter-spacing: -0.05em; }" +
                "    .header p { margin: 5px 0 0 0; font-size: 14px; opacity: 0.8; }" +
                "    .content { padding: 30px; line-height: 1.6; font-size: 15px; color: #cbd5e1; }" +
                "    .card { background-color: #0f172a; border-radius: 8px; border: 1px solid #334155; padding: 20px; margin-bottom: 20px; }" +
                "    .card-title { font-size: 16px; font-weight: bold; color: #38bdf8; margin-top: 0; margin-bottom: 15px; border-bottom: 1px dashed #334155; padding-bottom: 8px; }" +
                "    .footer { text-align: center; padding: 20px; font-size: 12px; color: #64748b; background-color: #0f172a; border-top: 1px solid #334155; }" +
                "    .badge-buy { background-color: #064e3b; color: #34d399; padding: 2px 6px; border-radius: 4px; font-size: 12px; font-weight: bold; }" +
                "    .badge-sell { background-color: #7f1d1d; color: #f87171; padding: 2px 6px; border-radius: 4px; font-size: 12px; font-weight: bold; }" +
                "    .text-green { color: #10b981; font-weight: bold; }" +
                "    .text-red { color: #ef4444; font-weight: bold; }" +
                "    .text-blue { color: #3b82f6; font-weight: bold; }" +
                "  </style>" +
                "</head>" +
                "<body>" +
                "  <div class='container'>" +
                "    <div class='header'>" +
                "      <h1>" + title + "</h1>" +
                "      <p>" + subtitle + "</p>" +
                "    </div>" +
                "    <div class='content'>" +
                content +
                "    </div>" +
                "    <div class='footer'>" +
                "      본 메일은 Toss Auto Trading System에서 자동으로 발송되었습니다.<br>" +
                "      &copy; 2026 Antigravity. All rights reserved." +
                "    </div>" +
                "  </div>" +
                "</body>" +
                "</html>";
    }
}
