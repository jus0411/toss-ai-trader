package com.toss.trading.notification;

import com.toss.trading.domain.OrderRecord;

public interface NotificationService {
    
    /**
     * 매매 주문 체결 시 알림을 전송합니다.
     */
    void sendOrderAlert(OrderRecord order);

    /**
     * 일간 자산 리포트 및 수익률 현황을 발송합니다.
     */
    void sendDailyReport();

    /**
     * 텔레그램으로 커스텀 알림 문자열을 발송합니다.
     */
    void sendTelegramMessage(String message);
}
