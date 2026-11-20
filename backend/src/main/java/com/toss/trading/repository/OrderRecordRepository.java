package com.toss.trading.repository;

import com.toss.trading.domain.OrderRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderRecordRepository extends JpaRepository<OrderRecord, Long> {
    List<OrderRecord> findAllByOrderByOrderTimeDesc();
    List<OrderRecord> findByStatus(String status);
    List<OrderRecord> findAllByOrderTimeAfter(java.time.LocalDateTime time);
}
