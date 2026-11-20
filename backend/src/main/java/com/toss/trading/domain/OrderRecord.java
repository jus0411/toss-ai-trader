package com.toss.trading.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false)
    private String stockCode;

    @Column(name = "stock_name", nullable = false)
    private String stockName;

    @Column(name = "order_type", nullable = false)
    private String orderType; // BUY, SELL

    @Column(name = "price", nullable = false)
    private Double price;

    @Column(name = "quantity", nullable = false)
    private Double quantity;

    @Column(name = "status", nullable = false)
    private String status; // ORDERED, FILLED, CANCELLED

    @Column(name = "order_time", nullable = false)
    private LocalDateTime orderTime;

    @Column(name = "trading_mode", nullable = false)
    private String tradingMode; // MOCK, REAL
}
