package com.toss.trading.repository;

import com.toss.trading.domain.AssetHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface AssetHistoryRepository extends JpaRepository<AssetHistory, Long> {
    List<AssetHistory> findAllByOrderByRecordDateAsc();
    List<AssetHistory> findAllByOrderByRecordTimeAsc();
    Optional<AssetHistory> findByRecordDate(LocalDate recordDate);
    Optional<AssetHistory> findFirstByOrderByRecordTimeDesc();
}
