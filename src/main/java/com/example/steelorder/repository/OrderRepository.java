package com.example.steelorder.repository;

import com.example.steelorder.entity.Order;
import com.example.steelorder.entity.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    long countByOrderedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByStatus(OrderStatus status);
    List<Order> findByStatus(OrderStatus status);
    List<Order> findByStatusOrderByDesiredDeliveryDateAsc(OrderStatus status);
    List<Order> findByStatusInOrderByDesiredDeliveryDateAsc(List<OrderStatus> statuses);
    List<Order> findByStatusOrderByShippedAtDesc(OrderStatus status);
    List<Order> findBySteelSheetAndStatusOrderByOrderedAtAsc(com.example.steelorder.entity.SteelSheet steelSheet,
                                                             OrderStatus status);
}

