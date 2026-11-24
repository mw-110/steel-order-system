package com.example.steelorder.service;

import com.example.steelorder.dto.ArrivalForm;
import com.example.steelorder.dto.ArrivalHistoryViewDto;
import com.example.steelorder.dto.SteelArrivalOptionDto;
import com.example.steelorder.entity.ArrivalHistory;
import com.example.steelorder.entity.Coil;
import com.example.steelorder.entity.Order;
import com.example.steelorder.entity.OrderAllocation;
import com.example.steelorder.entity.SteelSheet;
import com.example.steelorder.entity.enums.CoilStatus;
import com.example.steelorder.entity.enums.OrderStatus;
import com.example.steelorder.repository.ArrivalHistoryRepository;
import com.example.steelorder.repository.CoilRepository;
import com.example.steelorder.repository.OrderAllocationRepository;
import com.example.steelorder.repository.OrderRepository;
import com.example.steelorder.repository.SteelSheetRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class ArrivalService {

    private final SteelSheetRepository steelSheetRepository;
    private final CoilRepository coilRepository;
    private final OrderRepository orderRepository;
    private final OrderAllocationRepository orderAllocationRepository;
    private final ArrivalHistoryRepository arrivalHistoryRepository;

    public ArrivalService(SteelSheetRepository steelSheetRepository,
                          CoilRepository coilRepository,
                          OrderRepository orderRepository,
                          OrderAllocationRepository orderAllocationRepository,
                          ArrivalHistoryRepository arrivalHistoryRepository) {
        this.steelSheetRepository = steelSheetRepository;
        this.coilRepository = coilRepository;
        this.orderRepository = orderRepository;
        this.orderAllocationRepository = orderAllocationRepository;
        this.arrivalHistoryRepository = arrivalHistoryRepository;
    }

    @Transactional(readOnly = true)
    public List<SteelArrivalOptionDto> getSteelArrivalOptions() {
        return steelSheetRepository.findAll().stream()
                .map(sheet -> {
                    List<Order> backorders = orderRepository.findBySteelSheetAndStatusOrderByOrderedAtAsc(sheet, OrderStatus.BACKORDERED);
                    long count = backorders.size();
                    int shortage = backorders.stream()
                            .mapToInt(order -> Math.max(0, order.getOrderWeight() - order.getAllocatedWeight()))
                            .sum();
                    return new SteelArrivalOptionDto(
                            sheet.getSteelSheetId(),
                            sheet.getSteelCode(),
                            sheet.getSteelName(),
                            sheet.getThickness(),
                            sheet.getWidth(),
                            count,
                            shortage
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ArrivalHistoryViewDto> getRecentArrivals() {
        return arrivalHistoryRepository.findTop30ByOrderByCreatedAtDesc().stream()
                .map(history -> new ArrivalHistoryViewDto(
                        history.getCreatedAt(),
                        history.getCoil().getCoilNumber(),
                        history.getSteelSheet().getSteelCode(),
                        history.getSteelSheet().getSteelName(),
                        history.getSteelSheet().getThickness(),
                        history.getSteelSheet().getWidth(),
                        history.getArrivalWeight(),
                        history.getCoil().getAvailableWeight(),
                        history.getAutoAllocatedInfo(),
                        history.getAutoAllocatedInfo() != null && !history.getAutoAllocatedInfo().isBlank()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ArrivalForm buildDefaultForm(Long steelSheetId) {
        return new ArrivalForm(
                steelSheetId,
                generateCoilNumber(),
                null,
                LocalDate.now()
        );
    }

    @Transactional
    public ArrivalResult registerArrival(ArrivalForm form) {
        if (form.coilNumber() == null || form.coilNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("コイル番号を入力してください");
        }
        if (coilRepository.existsByCoilNumber(form.coilNumber().trim())) {
            throw new IllegalArgumentException("同じコイル番号が既に存在します: " + form.coilNumber());
        }

        SteelSheet steelSheet = steelSheetRepository.findById(form.steelSheetId())
                .orElseThrow(() -> new IllegalArgumentException("鋼板が見つかりません"));

        Coil coil = new Coil();
        coil.setCoilNumber(form.coilNumber().trim());
        coil.setSteelSheet(steelSheet);
        coil.setInitialWeight(form.arrivalWeight());
        coil.setPhysicalWeight(form.arrivalWeight());
        coil.setAvailableWeight(form.arrivalWeight());
        coil.setStatus(CoilStatus.AVAILABLE);
        coil.setArrivalDate(form.arrivalDate());
        try {
            coilRepository.save(coil);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("同じコイル番号が既に存在します: " + form.coilNumber());
        }

        List<String> autoAllocations = new ArrayList<>();
        List<Order> backorders = orderRepository
                .findBySteelSheetAndStatusOrderByOrderedAtAsc(steelSheet, OrderStatus.BACKORDERED);

        for (Order order : backorders) {
            if (coil.getAvailableWeight() <= 0) {
                break;
            }

            int required = Math.max(0, order.getOrderWeight() - order.getAllocatedWeight());
            if (required == 0) {
                continue;
            }
            if (coil.getAvailableWeight() < required) {
                continue;
            }

            coil.setAvailableWeight(coil.getAvailableWeight() - required);
            coil.setStatus(coil.getAvailableWeight() == 0 ? CoilStatus.FULLY_ALLOCATED : CoilStatus.PARTIALLY_ALLOCATED);

            order.setStatus(OrderStatus.ALLOCATED);
            order.setAllocatedWeight(order.getOrderWeight());
            orderRepository.save(order);

            OrderAllocation allocation = new OrderAllocation();
            allocation.setOrder(order);
            allocation.setCoil(coil);
            allocation.setAllocatedWeight(required);
            allocation.setAllocatedAt(LocalDateTime.now());
            orderAllocationRepository.save(allocation);

            autoAllocations.add(order.getOrderNumber() + " (" + order.getCustomerName() + " / " + formatKg(required) + "kg)");
        }

        ArrivalHistory history = new ArrivalHistory();
        history.setCoil(coil);
        history.setSteelSheet(steelSheet);
        history.setArrivalWeight(form.arrivalWeight());
        history.setArrivalDate(form.arrivalDate());
        history.setCreatedAt(LocalDateTime.now());
        history.setAutoAllocatedInfo(autoAllocations.isEmpty() ? null : String.join("、", autoAllocations));
        arrivalHistoryRepository.save(history);

        if (autoAllocations.isEmpty()) {
            return new ArrivalResult("入庫を登録しました", ResultLevel.INFO);
        }
        return new ArrivalResult(
                "入庫を登録し、同時に引当待ちだった注文（" + String.join("、", autoAllocations) + "）へ自動引当を実行しました！",
                ResultLevel.SUCCESS
        );
    }

    private String generateCoilNumber() {
        String prefix = "CL-" + YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";
        return prefix + String.format("%04d", coilRepository.count() + 1);
    }

    private String formatKg(int value) {
        return String.format("%,d", value);
    }

    public record ArrivalResult(String message, ResultLevel level) {
    }

    public enum ResultLevel {
        SUCCESS,
        INFO
    }
}
