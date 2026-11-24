package com.example.steelorder.service;

import com.example.steelorder.dto.ShipmentViewDto;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ShipmentServiceTest {

    private static final AtomicInteger TEST_SEQ = new AtomicInteger(2000);

    @Autowired
    private ShipmentService shipmentService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderAllocationRepository orderAllocationRepository;

    @Autowired
    private CoilRepository coilRepository;

    @Autowired
    private SteelSheetRepository steelSheetRepository;

    @Test
    @DisplayName("統合: ステータスフィルタallはALLOCATED/SHIPPEDのみ返す")
    void getShipmentOrders_returnsAllocatedAndShipped_whenFilterAll() {
        SteelSheet sheet = createSteelSheet("SHIP-ALL");
        Coil coil = createCoil(sheet, "CL-SHIP-ALL", 4000, 4000, 4000, CoilStatus.AVAILABLE);
        Order allocated = createOrder(sheet, "ORD-SHIP-AL", OrderStatus.ALLOCATED, 2000, 2000, LocalDate.now());
        Order shipped = createOrder(sheet, "ORD-SHIP-SH", OrderStatus.SHIPPED, 1500, 1500, LocalDate.now().plusDays(1));
        Order backordered = createOrder(sheet, "ORD-SHIP-BO", OrderStatus.BACKORDERED, 1000, 0, LocalDate.now().plusDays(2));
        createAllocation(allocated, coil, 2000);
        createAllocation(shipped, coil, 1500);

        List<ShipmentViewDto> views = shipmentService.getShipmentOrders("all");

        List<ShipmentViewDto> ours = views.stream()
                .filter(v -> v.orderNumber().equals(allocated.getOrderNumber())
                        || v.orderNumber().equals(shipped.getOrderNumber())
                        || v.orderNumber().equals(backordered.getOrderNumber()))
                .toList();

        assertThat(ours).hasSize(2);
        assertThat(ours).extracting(ShipmentViewDto::status)
                .containsExactlyInAnyOrder(OrderStatus.ALLOCATED.name(), OrderStatus.SHIPPED.name());
        assertThat(ours).extracting(ShipmentViewDto::orderNumber)
                .doesNotContain(backordered.getOrderNumber());
    }

    @Test
    @DisplayName("統合: ステータスフィルタallocatedは引当済のみ返す")
    void getShipmentOrders_returnsOnlyAllocated_whenFilterAllocated() {
        SteelSheet sheet = createSteelSheet("SHIP-FILTER");
        Coil coil = createCoil(sheet, "CL-SHIP-FLT", 3000, 3000, 3000, CoilStatus.AVAILABLE);
        Order allocated = createOrder(sheet, "ORD-FILTER-AL", OrderStatus.ALLOCATED, 1000, 1000, LocalDate.now());
        Order shipped = createOrder(sheet, "ORD-FILTER-SH", OrderStatus.SHIPPED, 1000, 1000, LocalDate.now());
        createAllocation(allocated, coil, 1000);
        createAllocation(shipped, coil, 1000);

        List<ShipmentViewDto> views = shipmentService.getShipmentOrders("allocated");

        List<ShipmentViewDto> ours = views.stream()
                .filter(v -> v.orderNumber().equals(allocated.getOrderNumber())
                        || v.orderNumber().equals(shipped.getOrderNumber()))
                .toList();

        assertThat(ours).hasSize(1);
        assertThat(ours.getFirst().orderNumber()).isEqualTo(allocated.getOrderNumber());
        assertThat(ours.getFirst().status()).isEqualTo(OrderStatus.ALLOCATED.name());
    }

    @Test
    @DisplayName("統合: 出荷確定で注文がSHIPPEDになり、引当コイル実在庫を減算する")
    void confirmShipment_updatesOrderAndCoils_whenValidAllocatedOrder() {
        SteelSheet sheet = createSteelSheet("SHIP-CONFIRM");
        Coil c1 = createCoil(sheet, "CL-SHIP-C1", 1000, 700, 0, CoilStatus.PARTIALLY_ALLOCATED);
        Coil c2 = createCoil(sheet, "CL-SHIP-C2", 1000, 300, 0, CoilStatus.FULLY_ALLOCATED);
        Order allocated = createOrder(sheet, "ORD-CONFIRM", OrderStatus.ALLOCATED, 700, 700, LocalDate.now());
        createAllocation(allocated, c1, 400);
        createAllocation(allocated, c2, 300);

        shipmentService.confirmShipment(allocated.getOrderId());

        Order shipped = orderRepository.findById(allocated.getOrderId()).orElseThrow();
        Coil reloadedC1 = coilRepository.findById(c1.getCoilId()).orElseThrow();
        Coil reloadedC2 = coilRepository.findById(c2.getCoilId()).orElseThrow();

        assertThat(shipped.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(shipped.getShippedAt()).isNotNull();
        assertThat(reloadedC1.getPhysicalWeight()).isEqualTo(300);
        assertThat(reloadedC1.getStatus()).isEqualTo(CoilStatus.PARTIALLY_ALLOCATED);
        assertThat(reloadedC2.getPhysicalWeight()).isZero();
        assertThat(reloadedC2.getStatus()).isEqualTo(CoilStatus.DEPLETED);
    }

    @Test
    @DisplayName("統合: ALLOCATED以外の注文は出荷確定できない")
    void confirmShipment_throwsWhenStatusIsNotAllocated() {
        SteelSheet sheet = createSteelSheet("SHIP-INVALID");
        Order shipped = createOrder(sheet, "ORD-INVALID", OrderStatus.SHIPPED, 500, 500, LocalDate.now());

        assertThatThrownBy(() -> shipmentService.confirmShipment(shipped.getOrderId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("出荷待ち（引当済）の注文のみ出荷確定できます");
    }

    @Test
    @DisplayName("統合: 引当明細がないALLOCATED注文は出荷確定できない")
    void confirmShipment_throwsWhenAllocationsMissing() {
        SteelSheet sheet = createSteelSheet("SHIP-NOALLOC");
        Order allocated = createOrder(sheet, "ORD-NOALLOC", OrderStatus.ALLOCATED, 800, 800, LocalDate.now());

        assertThatThrownBy(() -> shipmentService.confirmShipment(allocated.getOrderId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("引当明細がないため出荷確定できません");
    }

    @Test
    @DisplayName("統合: 実在庫不足コイルがあると出荷確定時に業務例外")
    void confirmShipment_throwsWhenPhysicalStockInsufficient() {
        SteelSheet sheet = createSteelSheet("SHIP-STOCK");
        Coil c1 = createCoil(sheet, "CL-SHIP-STOCK", 1000, 200, 0, CoilStatus.FULLY_ALLOCATED);
        Order allocated = createOrder(sheet, "ORD-STOCK", OrderStatus.ALLOCATED, 300, 300, LocalDate.now());
        createAllocation(allocated, c1, 300);

        assertThatThrownBy(() -> shipmentService.confirmShipment(allocated.getOrderId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("実在庫が不足しています");
    }

    private SteelSheet createSteelSheet(String codePrefix) {
        int seq = TEST_SEQ.incrementAndGet();
        SteelSheet sheet = new SteelSheet();
        sheet.setSteelCode(codePrefix + "-" + seq);
        sheet.setSteelName("出荷テスト鋼板-" + seq);
        sheet.setSteelGrade("SPCC");
        sheet.setUsageCategory("用途");
        sheet.setThickness(new BigDecimal("1.00"));
        sheet.setWidth(1000);
        sheet.setSafetyStockWeight(10000);
        return steelSheetRepository.saveAndFlush(sheet);
    }

    private Order createOrder(SteelSheet sheet,
                              String orderPrefix,
                              OrderStatus status,
                              int orderWeight,
                              int allocatedWeight,
                              LocalDate desiredDeliveryDate) {
        int seq = TEST_SEQ.incrementAndGet();
        Order order = new Order();
        order.setOrderNumber(orderPrefix + "-" + seq);
        order.setCustomerName("顧客-" + seq);
        order.setSteelSheet(sheet);
        order.setOrderWeight(orderWeight);
        order.setAllocatedWeight(allocatedWeight);
        order.setDesiredDeliveryDate(desiredDeliveryDate);
        order.setStatus(status);
        order.setOrderedAt(LocalDateTime.now().minusDays(1));
        if (status == OrderStatus.SHIPPED) {
            order.setShippedAt(LocalDateTime.now().minusHours(1));
        }
        return orderRepository.saveAndFlush(order);
    }

    private Coil createCoil(SteelSheet sheet,
                            String coilPrefix,
                            int initialWeight,
                            int physicalWeight,
                            int availableWeight,
                            CoilStatus status) {
        int seq = TEST_SEQ.incrementAndGet();
        Coil coil = new Coil();
        coil.setCoilNumber(coilPrefix + "-" + seq);
        coil.setSteelSheet(sheet);
        coil.setInitialWeight(initialWeight);
        coil.setPhysicalWeight(physicalWeight);
        coil.setAvailableWeight(availableWeight);
        coil.setStatus(status);
        coil.setArrivalDate(LocalDate.now().minusDays(5));
        return coilRepository.saveAndFlush(coil);
    }

    private OrderAllocation createAllocation(Order order, Coil coil, int allocatedWeight) {
        OrderAllocation allocation = new OrderAllocation();
        allocation.setOrder(order);
        allocation.setCoil(coil);
        allocation.setAllocatedWeight(allocatedWeight);
        allocation.setAllocatedAt(LocalDateTime.now().minusHours(2));
        return orderAllocationRepository.saveAndFlush(allocation);
    }
}
