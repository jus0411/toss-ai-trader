package com.toss.trading.repository;

import com.toss.trading.domain.TradingDiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TradingDiaryRepository extends JpaRepository<TradingDiary, Long> {
    Optional<TradingDiary> findByDiaryDate(String diaryDate);
}
