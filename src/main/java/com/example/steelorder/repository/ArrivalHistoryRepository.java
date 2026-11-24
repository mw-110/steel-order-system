package com.example.steelorder.repository;

import com.example.steelorder.entity.ArrivalHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArrivalHistoryRepository extends JpaRepository<ArrivalHistory, Long> {
    List<ArrivalHistory> findTop30ByOrderByCreatedAtDesc();
}
