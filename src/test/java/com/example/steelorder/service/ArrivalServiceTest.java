package com.example.steelorder.service;

import com.example.steelorder.dto.ArrivalForm;
import com.example.steelorder.entity.Coil;
import com.example.steelorder.entity.Order;
import com.example.steelorder.entity.SteelSheet;
import com.example.steelorder.entity.enums.CoilStatus;
import com.example.steelorder.entity.enums.OrderStatus;
import com.example.steelorder.repository.CoilRepository;
import com.example.steelorder.repository.OrderAllocationRepository;
import com.example.steelorder.repository.OrderRepository;
import com.example.steelorder.repository.SteelSheetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ArrivalServiceTest {

    private static final AtomicInteger TEST_SEQ = new AtomicInteger(1000);

    @Autowired
    private ArrivalService arrivalService;

    @Autowired
    private SteelSheetRepository steelSheetRepository;

    @Autowired
    private CoilRepository coilRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderAllocationRepository orderAllocationRepository;

    @Test
    @DisplayName("正常系（通常入荷・複数回入庫の独立管理）: 100kgを2回入荷すると2本の独立コイルとして登録される")
    void registerArrival_createsIndependentCoils_whenNoBackorders() {
        SteelSheet sheet = createSteelSheet("ARR-NORMAL");

        arrivalService.registerArrival(new ArrivalForm(
                sheet.getSteelSheetId(),
                uniqueCoilNumber("CL-ARR-N1"),
                100,
                LocalDate.now().minusDays(1)
        ));
        arrivalService.registerArrival(new ArrivalForm(
                sheet.getSteelSheetId(),
                uniqueCoilNumber("CL-ARR-N2"),
                100,
                LocalDate.now()
        ));

        List<Coil> coils = coilRepository.findBySteelSheetAndStatusNotOrderByArrivalDateAsc(sheet, CoilStatus.DEPLETED);

        assertThat(coils).hasSize(2);
        assertThat(coils).extracting(Coil::getInitialWeight).containsExactlyInAnyOrder(100, 100);
        assertThat(coils).extracting(Coil::getAvailableWeight).containsExactlyInAnyOrder(100, 100);
        assertThat(coils).extracting(Coil::getStatus).containsOnly(CoilStatus.AVAILABLE);
    }

    @Test
    @DisplayName("正常系（バックオーダー自動充当・FIFO優先順位）: 古い8,000kg注文のみALLOCATEDになり新しい注文はBACKORDEREDのまま")
    void registerArrival_autoAllocatesOnlyOldestBackorder_whenArrivalIs10000() {
        SteelSheet sheet = createSteelSheet("ARR-FIFO");
        Order orderA = createBackorder(sheet, "ORD-A", 8000, LocalDateTime.now().minusDays(2));
        Order orderB = createBackorder(sheet, "ORD-B", 8000, LocalDateTime.now().minusDays(1));

        ArrivalService.ArrivalResult result = arrivalService.registerArrival(new ArrivalForm(
                sheet.getSteelSheetId(),
                uniqueCoilNumber("CL-ARR-FIFO"),
                10000,
                LocalDate.now()
        ));

        Order reloadedA = orderRepository.findById(orderA.getOrderId()).orElseThrow();
        Order reloadedB = orderRepository.findById(orderB.getOrderId()).orElseThrow();
        Coil createdCoil = latestCoilBySheet(sheet.getSteelSheetId());

        assertThat(result.level()).isEqualTo(ArrivalService.ResultLevel.SUCCESS);
        assertThat(reloadedA.getStatus()).isEqualTo(OrderStatus.ALLOCATED);
        assertThat(reloadedA.getAllocatedWeight()).isEqualTo(8000);
        assertThat(reloadedB.getStatus()).isEqualTo(OrderStatus.BACKORDERED);
        assertThat(reloadedB.getAllocatedWeight()).isZero();
        assertThat(createdCoil.getAvailableWeight()).isEqualTo(2000);
        assertThat(createdCoil.getStatus()).isEqualTo(CoilStatus.PARTIALLY_ALLOCATED);
        assertThat(orderAllocationRepository.findByOrder(reloadedA)).hasSize(1);
        assertThat(orderAllocationRepository.findByOrder(reloadedB)).isEmpty();
    }

    @Test
    @DisplayName("正常系（境界値・入荷重量不足）: 8,000kgバックオーダーに7,999kg入荷では自動引当されない")
    void registerArrival_doesNotAllocate_whenArrivalWeightIsOneKgShort() {
        SteelSheet sheet = createSteelSheet("ARR-BOUNDARY");
        Order backorder = createBackorder(sheet, "ORD-BOUNDARY", 8000, LocalDateTime.now().minusHours(4));

        ArrivalService.ArrivalResult result = arrivalService.registerArrival(new ArrivalForm(
                sheet.getSteelSheetId(),
                uniqueCoilNumber("CL-ARR-BND"),
                7999,
                LocalDate.now()
        ));

        Order reloaded = orderRepository.findById(backorder.getOrderId()).orElseThrow();
        Coil createdCoil = latestCoilBySheet(sheet.getSteelSheetId());

        assertThat(result.level()).isEqualTo(ArrivalService.ResultLevel.INFO);
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.BACKORDERED);
        assertThat(reloaded.getAllocatedWeight()).isZero();
        assertThat(createdCoil.getAvailableWeight()).isEqualTo(7999);
        assertThat(createdCoil.getStatus()).isEqualTo(CoilStatus.AVAILABLE);
        assertThat(orderAllocationRepository.findByOrder(reloaded)).isEmpty();
    }

    @Test
    @DisplayName("異常系（コイル番号重複エラー）: 既存コイル番号での入荷登録は例外になる")
    void registerArrival_throwsException_whenCoilNumberDuplicated() {
        SteelSheet sheet = createSteelSheet("ARR-DUP");
        String duplicatedCoilNumber = uniqueCoilNumber("CL-ARR-DUP");

        arrivalService.registerArrival(new ArrivalForm(
                sheet.getSteelSheetId(),
                duplicatedCoilNumber,
                1000,
                LocalDate.now().minusDays(1)
        ));

        assertThatThrownBy(() -> arrivalService.registerArrival(new ArrivalForm(
                sheet.getSteelSheetId(),
                duplicatedCoilNumber,
                1200,
                LocalDate.now()
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("同じコイル番号が既に存在します");
    }

    private SteelSheet createSteelSheet(String codePrefix) {
        int seq = TEST_SEQ.incrementAndGet();
        SteelSheet sheet = new SteelSheet();
        sheet.setSteelCode(codePrefix + "-" + seq);
        sheet.setSteelName("入荷テスト鋼板-" + seq);
        sheet.setSteelGrade("JAC270C");
        sheet.setUsageCategory("用途");
        sheet.setThickness(new BigDecimal("1.40"));
        sheet.setWidth(1500);
        sheet.setSafetyStockWeight(10000);
        return steelSheetRepository.saveAndFlush(sheet);
    }

    private Order createBackorder(SteelSheet sheet, String orderPrefix, int weight, LocalDateTime orderedAt) {
        int seq = TEST_SEQ.incrementAndGet();
        Order order = new Order();
        order.setOrderNumber(orderPrefix + "-" + seq);
        order.setCustomerName("顧客-" + seq);
        order.setSteelSheet(sheet);
        order.setOrderWeight(weight);
        order.setAllocatedWeight(0);
        order.setDesiredDeliveryDate(LocalDate.now().plusDays(7));
        order.setStatus(OrderStatus.BACKORDERED);
        order.setOrderedAt(orderedAt);
        return orderRepository.saveAndFlush(order);
    }

    private Coil latestCoilBySheet(Long steelSheetId) {
        return coilRepository.findAll().stream()
                .filter(coil -> coil.getSteelSheet().getSteelSheetId().equals(steelSheetId))
                .max(Comparator.comparing(Coil::getCoilId))
                .orElseThrow();
    }

    private String uniqueCoilNumber(String prefix) {
        return prefix + "-" + TEST_SEQ.incrementAndGet();
    }
}
