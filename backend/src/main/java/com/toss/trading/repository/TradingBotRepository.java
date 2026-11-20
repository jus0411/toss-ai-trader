package com.toss.trading.repository;

import com.toss.trading.domain.TradingBot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradingBotRepository extends JpaRepository<TradingBot, Long> {
    List<TradingBot> findByActive(boolean active);
    Optional<TradingBot> findByStockCode(String stockCode);
}
