package com.example.steelorder.repository;

import com.example.steelorder.entity.Order;
import com.example.steelorder.entity.OrderAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderAllocationRepository extends JpaRepository<OrderAllocation, Long> {
    List<OrderAllocation> findByOrder(Order order);
    List<OrderAllocation> findByOrderOrderByAllocationIdAsc(Order order);
    void deleteByOrder(Order order);
}
