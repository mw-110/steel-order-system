package com.example.steelorder.service;

import com.example.steelorder.dto.ShipmentViewDto;
import com.example.steelorder.entity.Coil;
import com.example.steelorder.entity.Order;
import com.example.steelorder.entity.OrderAllocation;
import com.example.steelorder.entity.enums.CoilStatus;
import com.example.steelorder.entity.enums.OrderStatus;
import com.example.steelorder.repository.CoilRepository;
import com.example.steelorder.repository.OrderAllocationRepository;
import com.example.steelorder.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShipmentService {

    private final OrderRepository orderRepository;
    private final OrderAllocationRepository orderAllocationRepository;
    private final CoilRepository coilRepository;

    public ShipmentService(OrderRepository orderRepository,
                           OrderAllocationRepository orderAllocationRepository,
                           CoilRepository coilRepository) {
        this.orderRepository = orderRepository;
        this.orderAllocationRepository = orderAllocationRepository;
        this.coilRepository = coilRepository;
    }

    @Transactional(readOnly = true)
    public List<ShipmentViewDto> getShipmentOrders(String statusFilter) {
        List<Order> orders = switch (normalizeFilter(statusFilter)) {
            case "allocated" -> orderRepository.findByStatusOrderByDesiredDeliveryDateAsc(OrderStatus.ALLOCATED);
            case "shipped" -> orderRepository.findByStatusOrderByDesiredDeliveryDateAsc(OrderStatus.SHIPPED);
            default -> orderRepository.findByStatusInOrderByDesiredDeliveryDateAsc(
                    List.of(OrderStatus.ALLOCATED, OrderStatus.SHIPPED));
        };

        LocalDate today = LocalDate.now();
        return orders.stream()
                .map(order -> toViewDto(order, today))
                .toList();
    }

    @Transactional
    public void confirmShipment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("注文が見つかりません"));

        if (order.getStatus() != OrderStatus.ALLOCATED) {
            throw new IllegalStateException("出荷待ち（引当済）の注文のみ出荷確定できます");
        }

        List<OrderAllocation> allocations = orderAllocationRepository.findByOrderOrderByAllocationIdAsc(order);
        if (allocations.isEmpty()) {
            throw new IllegalStateException("引当明細がないため出荷確定できません");
        }

        order.setStatus(OrderStatus.SHIPPED);
        order.setShippedAt(LocalDateTime.now());
        orderRepository.save(order);

        for (OrderAllocation allocation : allocations) {
            Coil coil = allocation.getCoil();
            int currentPhysical = coil.getPhysicalWeight() == null ? 0 : coil.getPhysicalWeight();
            int nextPhysical = currentPhysical - allocation.getAllocatedWeight();
            if (nextPhysical < 0) {
                throw new IllegalStateException("コイル " + coil.getCoilNumber() + " の実在庫が不足しています");
            }

            coil.setPhysicalWeight(nextPhysical);
            if (nextPhysical == 0) {
                coil.setStatus(CoilStatus.DEPLETED);
            }
            coilRepository.save(coil);
        }
    }

    private ShipmentViewDto toViewDto(Order order, LocalDate today) {
        List<String> details = orderAllocationRepository.findByOrderOrderByAllocationIdAsc(order).stream()
                .map(allocation -> allocation.getCoil().getCoilNumber()
                        + " (" + formatKg(allocation.getAllocatedWeight()) + "kg)")
                .toList();

        String statusLabel = switch (order.getStatus()) {
            case ALLOCATED -> "出荷待ち";
            case SHIPPED -> "出荷済";
            case BACKORDERED -> "引当待ち";
            case CANCELED -> "キャンセル";
        };

        String statusBadgeClass = switch (order.getStatus()) {
            case ALLOCATED -> "bg-success";
            case SHIPPED -> "bg-secondary";
            case BACKORDERED -> "bg-warning text-dark";
            case CANCELED -> "bg-danger";
        };

        return new ShipmentViewDto(
                order.getOrderId(),
                order.getOrderNumber(),
                order.getDesiredDeliveryDate(),
                order.getCustomerName(),
                order.getSteelSheet().getSteelCode(),
                order.getSteelSheet().getSteelName(),
                order.getSteelSheet().getSteelGrade(),
                order.getSteelSheet().getThickness(),
                order.getSteelSheet().getWidth(),
                order.getOrderWeight(),
                details,
                order.getStatus().name(),
                statusLabel,
                statusBadgeClass,
                order.getShippedAt(),
                order.getStatus() == OrderStatus.ALLOCATED && !order.getDesiredDeliveryDate().isAfter(today)
        );
    }

    private String formatKg(int value) {
        return String.format("%,d", value);
    }

    private String normalizeFilter(String statusFilter) {
        if (statusFilter == null) {
            return "all";
        }
        String normalized = statusFilter.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return "all";
        }
        return normalized;
    }
}
