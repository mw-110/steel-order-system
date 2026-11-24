package com.example.steelorder.service;

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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ReportServiceTest {

    private static final AtomicInteger TEST_SEQ = new AtomicInteger(4000);

    @Autowired
    private ReportService reportService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderAllocationRepository orderAllocationRepository;

    @Autowired
    private SteelSheetRepository steelSheetRepository;

    @Autowired
    private CoilRepository coilRepository;

    @Test
    @DisplayName("統合: 出荷実績CSVはBOM付きでヘッダーと使用コイル番号を出力できる")
    void generateShipmentCsv_outputsBomAndRows() {
        SteelSheet sheet = createSteelSheet("RPT-CSV");
        Coil c1 = createCoil(sheet, "CL-RPT-001", LocalDate.of(2026, 11, 1));
        Coil c2 = createCoil(sheet, "CL-RPT-002", LocalDate.of(2026, 11, 2));
        Order order = createOrder(sheet, "ORD-RPT-CSV", OrderStatus.SHIPPED, LocalDate.now().minusDays(1));
        createAllocation(order, c1, 300);
        createAllocation(order, c2, 200);

        byte[] csv = reportService.generateShipmentCsv();

        assertThat(csv).isNotEmpty();
        assertThat(csv[0]).isEqualTo((byte) 0xEF);
        assertThat(csv[1]).isEqualTo((byte) 0xBB);
        assertThat(csv[2]).isEqualTo((byte) 0xBF);

        String body = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
        assertThat(body).contains("注文番号");
        assertThat(body).contains(order.getOrderNumber());
        assertThat(body).contains(c1.getCoilNumber() + " / " + c2.getCoilNumber());
    }

    @Test
    @DisplayName("統合: ミルシートPDFをALLOCATED注文で生成できる")
    void generateMillSheetPdf_returnsPdfBytes_whenOrderIsAllocated() {
        SteelSheet sheet = createSteelSheet("RPT-PDF");
        Coil c1 = createCoil(sheet, "CL-PDF-001", LocalDate.of(2026, 11, 3));
        Order allocated = createOrder(sheet, "ORD-RPT-PDF", OrderStatus.ALLOCATED, LocalDate.now().plusDays(2));
        createAllocation(allocated, c1, 500);

        byte[] pdf = reportService.generateMillSheetPdf(allocated.getOrderId());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("統合: ミルシート対象の注文が存在しない場合は例外になる")
    void generateMillSheetPdf_throwsWhenOrderNotFound() {
        assertThatThrownBy(() -> reportService.generateMillSheetPdf(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("注文が見つかりません");
    }

    @Test
    @DisplayName("統合: ミルシートは引当待ち注文では発行できない")
    void generateMillSheetPdf_throwsWhenOrderStatusNotAllowed() {
        SteelSheet sheet = createSteelSheet("RPT-NG");
        Order backordered = createOrder(sheet, "ORD-RPT-NG", OrderStatus.BACKORDERED, LocalDate.now().plusDays(5));

        assertThatThrownBy(() -> reportService.generateMillSheetPdf(backordered.getOrderId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ミルシートは出荷待ち/出荷済の注文のみ発行できます");
    }

    private SteelSheet createSteelSheet(String codePrefix) {
        int seq = TEST_SEQ.incrementAndGet();
        SteelSheet sheet = new SteelSheet();
        sheet.setSteelCode(codePrefix + "-" + seq);
        sheet.setSteelName("帳票テスト鋼板-" + seq);
        sheet.setSteelGrade("SPCC");
        sheet.setUsageCategory("用途");
        sheet.setThickness(new BigDecimal("1.00"));
        sheet.setWidth(1000);
        sheet.setSafetyStockWeight(10000);
        return steelSheetRepository.saveAndFlush(sheet);
    }

    private Order createOrder(SteelSheet sheet, String orderPrefix, OrderStatus status, LocalDate desiredDeliveryDate) {
        int seq = TEST_SEQ.incrementAndGet();
        Order order = new Order();
        order.setOrderNumber(orderPrefix + "-" + seq);
        order.setCustomerName("顧客-" + seq);
        order.setSteelSheet(sheet);
        order.setOrderWeight(500);
        order.setAllocatedWeight(status == OrderStatus.ALLOCATED || status == OrderStatus.SHIPPED ? 500 : 0);
        order.setDesiredDeliveryDate(desiredDeliveryDate);
        order.setStatus(status);
        order.setOrderedAt(LocalDateTime.now().minusDays(2));
        if (status == OrderStatus.SHIPPED) {
            order.setShippedAt(LocalDateTime.now().minusHours(2));
        }
        return orderRepository.saveAndFlush(order);
    }

    private Coil createCoil(SteelSheet sheet, String coilPrefix, LocalDate arrivalDate) {
        int seq = TEST_SEQ.incrementAndGet();
        Coil coil = new Coil();
        coil.setCoilNumber(coilPrefix + "-" + seq);
        coil.setSteelSheet(sheet);
        coil.setInitialWeight(1000);
        coil.setPhysicalWeight(700);
        coil.setAvailableWeight(0);
        coil.setStatus(CoilStatus.FULLY_ALLOCATED);
        coil.setArrivalDate(arrivalDate);
        return coilRepository.saveAndFlush(coil);
    }

    private OrderAllocation createAllocation(Order order, Coil coil, int weight) {
        OrderAllocation allocation = new OrderAllocation();
        allocation.setOrder(order);
        allocation.setCoil(coil);
        allocation.setAllocatedWeight(weight);
        allocation.setAllocatedAt(LocalDateTime.now());
        return orderAllocationRepository.saveAndFlush(allocation);
    }
}
