package com.toss.trading.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "asset_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssetHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "record_time")
    private java.time.LocalDateTime recordTime;

    @Column(name = "cash_balance", nullable = false)
    private Double cashBalance;

    @Column(name = "stock_evaluation", nullable = false)
    private Double stockEvaluation;

    @Column(name = "total_asset", nullable = false)
    private Double totalAsset;
}
