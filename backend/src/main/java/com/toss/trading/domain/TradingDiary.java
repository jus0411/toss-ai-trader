package com.toss.trading.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "trading_diary")
public class TradingDiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "diary_date", unique = true, nullable = false)
    private String diaryDate; // YYYY-MM-DD 포맷

    @Column(name = "total_trades")
    private int totalTrades;

    @Lob
    @Column(name = "feedback_content", length = 5000)
    private String feedbackContent; // Gemini AI 피드백 텍스트

    @Column(name = "record_time")
    private LocalDateTime recordTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDiaryDate() {
        return diaryDate;
    }

    public void setDiaryDate(String diaryDate) {
        this.diaryDate = diaryDate;
    }

    public int getTotalTrades() {
        return totalTrades;
    }

    public void setTotalTrades(int totalTrades) {
        this.totalTrades = totalTrades;
    }

    public String getFeedbackContent() {
        return feedbackContent;
    }

    public void setFeedbackContent(String feedbackContent) {
        this.feedbackContent = feedbackContent;
    }

    public LocalDateTime getRecordTime() {
        return recordTime;
    }

    public void setRecordTime(LocalDateTime recordTime) {
        this.recordTime = recordTime;
    }
}
