package com.example.steelorder.service;

import com.example.steelorder.dto.OrderForm;
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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
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
class OrderServiceTest {

    private static final AtomicInteger TEST_SEQ = new AtomicInteger(1);

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderAllocationRepository allocationRepository;

    @Autowired
    private SteelSheetRepository steelSheetRepository;

    @Autowired
    private CoilRepository coilRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("正常系（1本引当）: 在庫が十分な場合は最古コイルから減算され、注文はALLOCATEDかつ引当明細1件になる")
    void registerOrder_allocatesFromOldestSingleCoil_whenEnoughStock() {
        SteelSheet sheet = createSteelSheet("ORD-SINGLE");
        Coil oldest = createCoil(sheet, "CL-ORD-S1-OLD", 8000, 8000, LocalDate.of(2026, 11, 1));
        Coil newest = createCoil(sheet, "CL-ORD-S1-NEW", 7000, 7000, LocalDate.of(2026, 11, 10));

        String message = orderService.registerOrder(
                new OrderForm(uniqueCustomer("single"), sheet.getSteelSheetId(), 5000, LocalDate.now().plusDays(7))
        );

        Order created = latestOrderBySheet(sheet.getSteelSheetId());
        Coil reloadedOldest = coilRepository.findById(oldest.getCoilId()).orElseThrow();
        Coil reloadedNewest = coilRepository.findById(newest.getCoilId()).orElseThrow();
        List<OrderAllocation> allocations = allocationRepository.findByOrderOrderByAllocationIdAsc(created);

        assertThat(message).isEqualTo("引当済で登録しました");
        assertThat(created.getStatus()).isEqualTo(OrderStatus.ALLOCATED);
        assertThat(created.getAllocatedWeight()).isEqualTo(5000);
        assertThat(reloadedOldest.getAvailableWeight()).isEqualTo(3000);
        assertThat(reloadedOldest.getStatus()).isEqualTo(CoilStatus.PARTIALLY_ALLOCATED);
        assertThat(reloadedNewest.getAvailableWeight()).isEqualTo(7000);
        assertThat(reloadedNewest.getStatus()).isEqualTo(CoilStatus.AVAILABLE);
        assertThat(allocations).hasSize(1);
        assertThat(allocations.getFirst().getCoil().getCoilId()).isEqualTo(oldest.getCoilId());
        assertThat(allocations.getFirst().getAllocatedWeight()).isEqualTo(5000);
    }

    @Test
    @DisplayName("正常系（FIFO複数コイル分割引当）: 1本目不足時は古いコイルを使い切り、2本目から不足分を引当てる")
    void registerOrder_splitsAcrossTwoCoils_whenFirstCoilInsufficient() {
        SteelSheet sheet = createSteelSheet("ORD-SPLIT");
        Coil first = createCoil(sheet, "CL-ORD-S2-001", 3000, 3000, LocalDate.of(2026, 11, 2));
        Coil second = createCoil(sheet, "CL-ORD-S2-002", 5000, 5000, LocalDate.of(2026, 11, 9));

        orderService.registerOrder(
                new OrderForm(uniqueCustomer("split"), sheet.getSteelSheetId(), 4500, LocalDate.now().plusDays(10))
        );

        Order created = latestOrderBySheet(sheet.getSteelSheetId());
        Coil reloadedFirst = coilRepository.findById(first.getCoilId()).orElseThrow();
        Coil reloadedSecond = coilRepository.findById(second.getCoilId()).orElseThrow();
        List<OrderAllocation> allocations = allocationRepository.findByOrderOrderByAllocationIdAsc(created);

        assertThat(created.getStatus()).isEqualTo(OrderStatus.ALLOCATED);
        assertThat(created.getAllocatedWeight()).isEqualTo(4500);
        assertThat(reloadedFirst.getAvailableWeight()).isZero();
        assertThat(reloadedFirst.getStatus()).isEqualTo(CoilStatus.FULLY_ALLOCATED);
        assertThat(reloadedSecond.getAvailableWeight()).isEqualTo(3500);
        assertThat(reloadedSecond.getStatus()).isEqualTo(CoilStatus.PARTIALLY_ALLOCATED);
        assertThat(allocations).hasSize(2);
        assertThat(allocations.get(0).getCoil().getCoilId()).isEqualTo(first.getCoilId());
        assertThat(allocations.get(0).getAllocatedWeight()).isEqualTo(3000);
        assertThat(allocations.get(1).getCoil().getCoilId()).isEqualTo(second.getCoilId());
        assertThat(allocations.get(1).getAllocatedWeight()).isEqualTo(1500);
    }

    @Test
    @DisplayName("正常系（境界値・在庫不足によるバックオーダー分岐）: 有効在庫合計+1kgの注文は引当なしでBACKORDEREDになる")
    void registerOrder_marksBackordered_whenOrderExceedsAvailableByOneKg() {
        SteelSheet sheet = createSteelSheet("ORD-BOUNDARY");
        createCoil(sheet, "CL-ORD-S3-001", 4000, 4000, LocalDate.of(2026, 11, 3));
        createCoil(sheet, "CL-ORD-S3-002", 3000, 3000, LocalDate.of(2026, 11, 8));

        String message = orderService.registerOrder(
                new OrderForm(uniqueCustomer("backorder"), sheet.getSteelSheetId(), 7001, LocalDate.now().plusDays(8))
        );

        Order created = latestOrderBySheet(sheet.getSteelSheetId());
        List<OrderAllocation> allocations = allocationRepository.findByOrder(created);

        assertThat(message).isEqualTo("在庫不足のため引当待ち（バックオーダー）で登録しました");
        assertThat(created.getStatus()).isEqualTo(OrderStatus.BACKORDERED);
        assertThat(created.getAllocatedWeight()).isZero();
        assertThat(allocations).isEmpty();
    }

    @Test
    @DisplayName("正常系（注文キャンセルによる引当解除）: ALLOCATED注文キャンセル時にコイル残量とステータスが復元される")
    void cancelOrder_restoresCoilState_whenAllocatedOrderIsCanceled() {
        SteelSheet sheet = createSteelSheet("ORD-CANCEL");
        Coil first = createCoil(sheet, "CL-ORD-S4-001", 3000, 3000, LocalDate.of(2026, 11, 4));
        Coil second = createCoil(sheet, "CL-ORD-S4-002", 5000, 5000, LocalDate.of(2026, 11, 6));

        orderService.registerOrder(
                new OrderForm(uniqueCustomer("cancel"), sheet.getSteelSheetId(), 4500, LocalDate.now().plusDays(6))
        );
        Order created = latestOrderBySheet(sheet.getSteelSheetId());

        String message = orderService.cancelOrder(created.getOrderId());

        Order canceled = orderRepository.findById(created.getOrderId()).orElseThrow();
        Coil reloadedFirst = coilRepository.findById(first.getCoilId()).orElseThrow();
        Coil reloadedSecond = coilRepository.findById(second.getCoilId()).orElseThrow();
        List<OrderAllocation> allocations = allocationRepository.findByOrder(canceled);

        assertThat(message).isEqualTo("注文をキャンセルし、引当を解除しました");
        assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(canceled.getAllocatedWeight()).isZero();
        assertThat(reloadedFirst.getAvailableWeight()).isEqualTo(3000);
        assertThat(reloadedFirst.getStatus()).isEqualTo(CoilStatus.AVAILABLE);
        assertThat(reloadedSecond.getAvailableWeight()).isEqualTo(5000);
        assertThat(reloadedSecond.getStatus()).isEqualTo(CoilStatus.AVAILABLE);
        assertThat(allocations).isEmpty();
    }

    @Test
    @DisplayName("異常系（出荷済注文のキャンセル不可）: SHIPPED注文のキャンセル要求は業務例外になる")
    void cancelOrder_throwsException_whenOrderAlreadyShipped() {
        SteelSheet sheet = createSteelSheet("ORD-SHIPPED");
        Order shipped = new Order();
        shipped.setOrderNumber("ORD-TEST-SHIPPED-" + TEST_SEQ.incrementAndGet());
        shipped.setCustomerName(uniqueCustomer("shipped"));
        shipped.setSteelSheet(sheet);
        shipped.setOrderWeight(1000);
        shipped.setAllocatedWeight(1000);
        shipped.setDesiredDeliveryDate(LocalDate.now().plusDays(5));
        shipped.setStatus(OrderStatus.SHIPPED);
        shipped.setOrderedAt(LocalDateTime.now().minusDays(1));
        shipped.setShippedAt(LocalDateTime.now());
        Order persisted = orderRepository.saveAndFlush(shipped);

        assertThatThrownBy(() -> orderService.cancelOrder(persisted.getOrderId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("キャンセルできません");
    }

    @Test
    @DisplayName("補完（楽観的ロック競合）: 同一コイルの競合更新でOptimisticLockingFailureExceptionが発生する")
    void optimisticLocking_throwsOnConflictingCoilUpdate() {
        SteelSheet sheet = createSteelSheet("ORD-OPT");
        Coil persisted = createCoil(sheet, "CL-ORD-OPT-001", 1000, 1000, LocalDate.now());

        Coil tx1Snapshot = coilRepository.findById(persisted.getCoilId()).orElseThrow();
        entityManager.detach(tx1Snapshot);
        Coil tx2Snapshot = coilRepository.findById(persisted.getCoilId()).orElseThrow();
        entityManager.detach(tx2Snapshot);

        tx1Snapshot.setAvailableWeight(900);
        coilRepository.saveAndFlush(tx1Snapshot);

        tx2Snapshot.setAvailableWeight(800);
        assertThatThrownBy(() -> coilRepository.saveAndFlush(tx2Snapshot))
                .isInstanceOf(OptimisticLockingFailureException.class);

        Coil actual = coilRepository.findById(persisted.getCoilId()).orElseThrow();
        assertThat(actual.getAvailableWeight()).isEqualTo(900);
    }

    private SteelSheet createSteelSheet(String codePrefix) {
        int seq = TEST_SEQ.incrementAndGet();
        SteelSheet sheet = new SteelSheet();
        sheet.setSteelCode(codePrefix + "-" + seq);
        sheet.setSteelName("テスト鋼板-" + seq);
        sheet.setSteelGrade("SPCC");
        sheet.setUsageCategory("用途");
        sheet.setThickness(new BigDecimal("1.20"));
        sheet.setWidth(1200);
        sheet.setSafetyStockWeight(1000);
        return steelSheetRepository.saveAndFlush(sheet);
    }

    private Coil createCoil(SteelSheet sheet, String coilNumber, int initialWeight, int availableWeight, LocalDate arrivalDate) {
        Coil coil = new Coil();
        coil.setCoilNumber(coilNumber + "-" + TEST_SEQ.incrementAndGet());
        coil.setSteelSheet(sheet);
        coil.setInitialWeight(initialWeight);
        coil.setPhysicalWeight(initialWeight);
        coil.setAvailableWeight(availableWeight);
        coil.setStatus(CoilStatus.AVAILABLE);
        coil.setArrivalDate(arrivalDate);
        return coilRepository.saveAndFlush(coil);
    }

    private String uniqueCustomer(String prefix) {
        return "顧客-" + prefix + "-" + TEST_SEQ.incrementAndGet();
    }

    private Order latestOrderBySheet(Long steelSheetId) {
        return orderRepository.findAll().stream()
                .filter(order -> order.getSteelSheet().getSteelSheetId().equals(steelSheetId))
                .max(Comparator.comparing(Order::getOrderId))
                .orElseThrow();
    }
}
