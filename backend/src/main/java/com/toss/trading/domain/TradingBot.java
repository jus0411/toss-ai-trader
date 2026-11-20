package com.toss.trading.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "trading_bot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TradingBot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, unique = true)
    private String stockCode;

    @Column(name = "stock_name", nullable = false)
    private String stockName;

    @Column(name = "algorithm_type", nullable = false)
    private String algorithmType; // ACCUMULATION, DROP_BUY, MA_CROSS

    @Column(name = "active", nullable = false)
    private boolean active = false;

    // JSON format string for parameters (e.g. {"amount": 10000, "intervalMinutes": 60})
    @Column(name = "parameters", length = 1000)
    private String parameters;

    @Column(name = "stop_loss_percent")
    private Double stopLossPercent; // 예: 3.0 이면 평균단가 대비 -3% 도달 시 매도

    @Column(name = "take_profit_percent")
    private Double takeProfitPercent; // 예: 10.0 이면 평균단가 대비 +10% 도달 시 매도

    @Column(name = "status_message", length = 500)
    private String statusMessage = "정지됨 / 감시 대기";
}
