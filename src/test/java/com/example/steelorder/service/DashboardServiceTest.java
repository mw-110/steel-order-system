package com.example.steelorder.service;

import com.example.steelorder.dto.DashboardAlertDto;
import com.example.steelorder.dto.DashboardSummaryDto;
import com.example.steelorder.entity.Coil;
import com.example.steelorder.entity.Order;
import com.example.steelorder.entity.SteelSheet;
import com.example.steelorder.entity.enums.CoilStatus;
import com.example.steelorder.entity.enums.OrderStatus;
import com.example.steelorder.repository.CoilRepository;
import com.example.steelorder.repository.OrderRepository;
import com.example.steelorder.repository.SteelSheetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DashboardServiceTest {

    private static final AtomicInteger TEST_SEQ = new AtomicInteger(3000);

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private SteelSheetRepository steelSheetRepository;

    @Autowired
    private CoilRepository coilRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("統合: getSummaryは当日受注数とステータス別件数を正しく集計する")
    void getSummary_countsTodayAndStatuses() {
        DashboardSummaryDto before = dashboardService.getSummary();

        SteelSheet sheet = createSteelSheet("DASH-SUMMARY", 10000);
        createOrder(sheet, "ORD-SUM-TODAY-1", OrderStatus.ALLOCATED, LocalDate.now().plusDays(1), LocalDateTime.now().minusHours(1));
        createOrder(sheet, "ORD-SUM-TODAY-2", OrderStatus.BACKORDERED, LocalDate.now().plusDays(2), LocalDateTime.now().minusMinutes(30));
        createOrder(sheet, "ORD-SUM-OLD", OrderStatus.SHIPPED, LocalDate.now().minusDays(1), LocalDateTime.now().minusDays(2));

        DashboardSummaryDto after = dashboardService.getSummary();

        assertThat(after.todayOrderCount()).isEqualTo(before.todayOrderCount() + 2);
        assertThat(after.allocatedCount()).isEqualTo(before.allocatedCount() + 1);
        assertThat(after.backorderedCount()).isEqualTo(before.backorderedCount() + 1);
    }

    @ParameterizedTest(name = "有効在庫={0}kg のときレベルは {1}")
    @CsvSource({
            "0,DANGER",
            "9999,WARNING",
            "10000,none"
    })
    @DisplayName("統合: 安全在庫しきい値の境界で在庫アラートレベルが変化する")
    void getAlerts_changesBySafetyStockBoundary(int availableWeight, String expectedLevel) {
        String code = "DASH-INV-" + TEST_SEQ.incrementAndGet();
        SteelSheet sheet = createSteelSheet(code, 10000);
        createCoil(
                sheet,
                "CL-" + code,
                Math.max(availableWeight, 1),
                Math.max(availableWeight, 1),
                availableWeight,
                availableWeight == 0 ? CoilStatus.FULLY_ALLOCATED : CoilStatus.AVAILABLE,
                LocalDate.now().minusDays(10)
        );

        List<DashboardAlertDto> alerts = dashboardService.getAlerts();
        List<DashboardAlertDto> inventoryAlerts = alerts.stream()
                .filter(alert -> alert.message().contains(code))
                .toList();

        if ("none".equals(expectedLevel)) {
            assertThat(inventoryAlerts).isEmpty();
        } else {
            assertThat(inventoryAlerts).hasSize(1);
            assertThat(inventoryAlerts.getFirst().level()).isEqualTo(expectedLevel);
        }
    }

    @ParameterizedTest(name = "納期が今日から{0}日のときレベルは{1}")
    @CsvSource({
            "0,DANGER",
            "1,WARNING",
            "3,WARNING",
            "4,none"
    })
    @DisplayName("統合: 納期接近アラートの境界値を判定できる")
    void getAlerts_deliveryBoundary(int offsetDays, String expectedLevel) {
        String code = "DASH-DEL-" + TEST_SEQ.incrementAndGet();
        SteelSheet sheet = createSteelSheet(code, 1000);
        createCoil(sheet, "CL-" + code, 5000, 5000, 5000, CoilStatus.AVAILABLE, LocalDate.now().minusDays(5));
        Order allocatedOrder = createOrder(
                sheet,
                "ORD-DEL-" + TEST_SEQ.incrementAndGet(),
                OrderStatus.ALLOCATED,
                LocalDate.now().plusDays(offsetDays),
                LocalDateTime.now().minusDays(1)
        );

        List<DashboardAlertDto> alerts = dashboardService.getAlerts();
        List<DashboardAlertDto> shippingAlerts = alerts.stream()
                .filter(alert -> alert.message().contains(allocatedOrder.getOrderNumber()))
                .toList();

        if ("none".equals(expectedLevel)) {
            assertThat(shippingAlerts).isEmpty();
        } else {
            assertThat(shippingAlerts).hasSize(1);
            assertThat(shippingAlerts.getFirst().level()).isEqualTo(expectedLevel);
        }
    }

    private SteelSheet createSteelSheet(String code, int safetyStock) {
        SteelSheet sheet = new SteelSheet();
        sheet.setSteelCode(code);
        sheet.setSteelName("鋼板-" + code);
        sheet.setSteelGrade("GRADE");
        sheet.setUsageCategory("用途");
        sheet.setThickness(new BigDecimal("1.00"));
        sheet.setWidth(1000);
        sheet.setSafetyStockWeight(safetyStock);
        return steelSheetRepository.saveAndFlush(sheet);
    }

    private Coil createCoil(SteelSheet sheet,
                            String coilNumber,
                            int initial,
                            int physical,
                            int available,
                            CoilStatus status,
                            LocalDate arrivalDate) {
        Coil coil = new Coil();
        coil.setCoilNumber(coilNumber);
        coil.setSteelSheet(sheet);
        coil.setInitialWeight(initial);
        coil.setPhysicalWeight(physical);
        coil.setAvailableWeight(available);
        coil.setStatus(status);
        coil.setArrivalDate(arrivalDate);
        return coilRepository.saveAndFlush(coil);
    }

    private Order createOrder(SteelSheet sheet,
                              String orderNo,
                              OrderStatus status,
                              LocalDate deliveryDate,
                              LocalDateTime orderedAt) {
        Order order = new Order();
        order.setOrderNumber(orderNo);
        order.setCustomerName("顧客");
        order.setSteelSheet(sheet);
        order.setOrderWeight(1000);
        order.setAllocatedWeight(status == OrderStatus.ALLOCATED || status == OrderStatus.SHIPPED ? 1000 : 0);
        order.setStatus(status);
        order.setDesiredDeliveryDate(deliveryDate);
        order.setOrderedAt(orderedAt);
        if (status == OrderStatus.SHIPPED) {
            order.setShippedAt(LocalDateTime.now().minusHours(1));
        }
        return orderRepository.saveAndFlush(order);
    }
}
