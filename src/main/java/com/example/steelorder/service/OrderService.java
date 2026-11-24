package com.example.steelorder.service;

import com.example.steelorder.dto.OrderAllocationDetailDto;
import com.example.steelorder.dto.OrderForm;
import com.example.steelorder.dto.OrderViewDto;
import com.example.steelorder.entity.Coil;
import com.example.steelorder.entity.Order;
import com.example.steelorder.entity.OrderAllocation;
import com.example.steelorder.entity.SteelSheet;
import com.example.steelorder.entity.enums.CoilStatus;
import com.example.steelorder.entity.enums.OrderStatus;
import com.example.steelorder.repository.CoilRepository;
import com.example.steelorder.repository.OrderAllocationRepository;
import com.example.steelorder.repository.OrderRepository;
import com.example.steelorder.repository.SteelSheetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderAllocationRepository allocationRepository;
    private final SteelSheetRepository steelSheetRepository;
    private final CoilRepository coilRepository;

    public OrderService(OrderRepository orderRepository,
                        OrderAllocationRepository allocationRepository,
                        SteelSheetRepository steelSheetRepository,
                        CoilRepository coilRepository) {
        this.orderRepository = orderRepository;
        this.allocationRepository = allocationRepository;
        this.steelSheetRepository = steelSheetRepository;
        this.coilRepository = coilRepository;
    }

    @Transactional
    public String registerOrder(OrderForm form) {
        if (form.customerName() == null || form.customerName().trim().isEmpty()) {
            throw new IllegalArgumentException("取引先名を入力してください");
        }

        SteelSheet sheet = steelSheetRepository.findById(form.steelSheetId())
                .orElseThrow(() -> new IllegalArgumentException("鋼板が見つかりません"));

        List<Coil> availableCoils = coilRepository
                .findBySteelSheetAndStatusNotOrderByArrivalDateAsc(sheet, CoilStatus.DEPLETED)
                .stream()
                .filter(coil -> coil.getAvailableWeight() != null && coil.getAvailableWeight() > 0)
                .sorted(Comparator.comparing(Coil::getArrivalDate))
                .toList();

        int availableWeight = availableCoils.stream()
                .mapToInt(coil -> coil.getAvailableWeight()).sum();

        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setCustomerName(form.customerName().trim());
        order.setSteelSheet(sheet);
        order.setOrderWeight(form.orderWeight());
        order.setDesiredDeliveryDate(form.desiredDeliveryDate());
        order.setOrderedAt(LocalDateTime.now());

        if (availableWeight < form.orderWeight()) {
            order.setStatus(OrderStatus.BACKORDERED);
            order.setAllocatedWeight(0);
            orderRepository.save(order);
            return "在庫不足のため引当待ち（バックオーダー）で登録しました";
        }

        order.setStatus(OrderStatus.ALLOCATED);
        order.setAllocatedWeight(form.orderWeight());
        orderRepository.save(order);

        int remaining = form.orderWeight();
        LocalDateTime allocatedAt = LocalDateTime.now();

        for (Coil coil : availableCoils) {
            if (remaining == 0) {
                break;
            }

            int allocated = Math.min(remaining, coil.getAvailableWeight());
            coil.setAvailableWeight(coil.getAvailableWeight() - allocated);
            coil.setStatus(coil.getAvailableWeight() == 0
                    ? CoilStatus.FULLY_ALLOCATED
                    : CoilStatus.PARTIALLY_ALLOCATED);
            coilRepository.save(coil);

            OrderAllocation allocation = new OrderAllocation();
            allocation.setOrder(order);
            allocation.setCoil(coil);
            allocation.setAllocatedWeight(allocated);
            allocation.setAllocatedAt(allocatedAt);
            allocationRepository.save(allocation);

            remaining -= allocated;
        }

        return "引当済で登録しました";
    }

    @Transactional
    public String cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("注文が見つかりません"));

        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.CANCELED) {
            throw new IllegalStateException("出荷済またはキャンセル済の注文はキャンセルできません");
        }

        if (order.getStatus() == OrderStatus.ALLOCATED) {
            List<OrderAllocation> allocations = allocationRepository.findByOrder(order);
            for (OrderAllocation allocation : allocations) {
                Coil coil = allocation.getCoil();
                int available = coil.getAvailableWeight() == null ? 0 : coil.getAvailableWeight();
                coil.setAvailableWeight(available + allocation.getAllocatedWeight());
                coil.setStatus(coil.getAvailableWeight() > 0
                        ? (coil.getInitialWeight() > coil.getAvailableWeight()
                            ? CoilStatus.PARTIALLY_ALLOCATED
                            : CoilStatus.AVAILABLE)
                        : CoilStatus.FULLY_ALLOCATED);
                coilRepository.save(coil);
            }
            allocationRepository.deleteByOrder(order);
        }

        order.setStatus(OrderStatus.CANCELED);
        order.setAllocatedWeight(0);
        orderRepository.save(order);
        return "注文をキャンセルし、引当を解除しました";
    }

    @Transactional(readOnly = true)
    public List<SteelSheet> findSteelSheets() {
        return steelSheetRepository.findAll();
    }

    @Transactional(readOnly = true)
    public int getAvailableWeight(SteelSheet sheet) {
        return coilRepository
                .findBySteelSheetAndStatusNotOrderByArrivalDateAsc(sheet, CoilStatus.DEPLETED)
                .stream()
                .mapToInt(coil -> coil.getAvailableWeight() == null ? 0 : coil.getAvailableWeight())
                .sum();
    }

    @Transactional(readOnly = true)
    public List<OrderViewDto> findAllOrders() {
        return orderRepository.findAll().stream()
                .sorted(Comparator.comparing(Order::getOrderedAt).reversed())
                .map(this::toViewDto)
                .toList();
    }

    private OrderViewDto toViewDto(Order order) {
        List<OrderAllocationDetailDto> allocations = allocationRepository.findByOrder(order).stream()
                .map(allocation -> new OrderAllocationDetailDto(
                        allocation.getCoil().getCoilNumber(),
                        allocation.getAllocatedWeight(),
                        allocation.getCoil().getArrivalDate()))
                .toList();

        String statusLabel = switch (order.getStatus()) {
            case ALLOCATED -> "引当済";
            case BACKORDERED -> "引当待ち";
            case SHIPPED -> "出荷済";
            case CANCELED -> "キャンセル";
        };

        String statusBadgeClass = switch (order.getStatus()) {
            case ALLOCATED -> "bg-success";
            case BACKORDERED -> "bg-warning text-dark";
            case SHIPPED -> "bg-secondary";
            case CANCELED -> "bg-danger";
        };

        SteelSheet sheet = order.getSteelSheet();
        return new OrderViewDto(
                order.getOrderId(),
                order.getOrderNumber(),
                order.getOrderedAt(),
                order.getCustomerName(),
                sheet.getSteelName(),
                sheet.getSteelGrade(),
                sheet.getThickness(),
                sheet.getWidth(),
                order.getOrderWeight(),
                order.getAllocatedWeight(),
                order.getDesiredDeliveryDate(),
                order.getStatus().name(),
                statusLabel,
                statusBadgeClass,
                allocations
        );
    }

    private String generateOrderNumber() {
        String prefix = "ORD-" + YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";
        long count = orderRepository.count();
        return prefix + String.format("%04d", count + 1);
    }
}
