package com.example.steelorder.controller;

import com.example.steelorder.config.SecurityConfig;
import com.example.steelorder.dto.ArrivalForm;
import com.example.steelorder.dto.ArrivalHistoryViewDto;
import com.example.steelorder.dto.DashboardAlertDto;
import com.example.steelorder.dto.DashboardSummaryDto;
import com.example.steelorder.dto.OrderViewDto;
import com.example.steelorder.dto.SteelArrivalOptionDto;
import com.example.steelorder.entity.SteelSheet;
import com.example.steelorder.service.ArrivalService;
import com.example.steelorder.service.CustomUserDetailsService;
import com.example.steelorder.service.DashboardService;
import com.example.steelorder.service.InventoryService;
import com.example.steelorder.service.OrderService;
import com.example.steelorder.service.ReportService;
import com.example.steelorder.service.ShipmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = {
        LoginController.class,
        DashboardController.class,
        InventoryController.class,
        ArrivalController.class,
        OrderController.class,
        ShipmentController.class
})
@Import(SecurityConfig.class)
class ScreenControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

        @MockitoBean
    private DashboardService dashboardService;

        @MockitoBean
    private InventoryService inventoryService;

        @MockitoBean
    private ArrivalService arrivalService;

        @MockitoBean
    private OrderService orderService;

        @MockitoBean
    private ShipmentService shipmentService;

        @MockitoBean
    private ReportService reportService;

        @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("画面: /login は未認証でも表示できる")
    void loginPage_isPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    @DisplayName("画面: / は未認証時にログインへリダイレクトされる")
    void dashboard_redirectsToLogin_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("画面: / は認証済で表示され、summary/alerts がモデルに入る")
    void dashboard_showsSummaryAndAlerts_whenAuthenticated() throws Exception {
        when(dashboardService.getSummary()).thenReturn(new DashboardSummaryDto(1, 2, 3));
        when(dashboardService.getAlerts()).thenReturn(List.of(
                new DashboardAlertDto("WARNING", "msg", "/orders", "確認")
        ));

        mockMvc.perform(get("/")
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES")))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("summary", "alerts", "activePage", "pageTitle"));
    }

    @Test
    @DisplayName("画面: /orders は一覧表示に必要なモデルを設定する")
    void orders_setsModelAttributes() throws Exception {
        SteelSheet sheet = new SteelSheet();
        sheet.setSteelSheetId(1L);
        sheet.setSteelCode("STL-WEB-01");
        sheet.setSteelName("鋼板");
        sheet.setSteelGrade("SPCC");
        sheet.setUsageCategory("用途");
        sheet.setThickness(new BigDecimal("1.00"));
        sheet.setWidth(1000);
        sheet.setSafetyStockWeight(10000);

        OrderViewDto order = new OrderViewDto(
                1L, "ORD-1", LocalDateTime.now(), "顧客", "鋼板", "SPCC",
                new BigDecimal("1.00"), 1000, 1000, 1000,
                LocalDate.now().plusDays(3), "ALLOCATED", "引当済", "bg-success", List.of()
        );

        when(orderService.findAllOrders()).thenReturn(List.of(order));
        when(orderService.findSteelSheets()).thenReturn(List.of(sheet));
        when(orderService.getAvailableWeight(sheet)).thenReturn(8000);

        mockMvc.perform(get("/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES")))
                .andExpect(status().isOk())
                .andExpect(view().name("orders"))
                .andExpect(model().attributeExists("orders", "steelSheets", "availableWeights", "orderForm"));
    }

    @Test
    @DisplayName("画面: /orders POST はバリデーションエラー時に /orders へ戻る")
    void registerOrder_redirectsWithValidationError() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES"))
                        .param("customerName", "")
                        .param("orderWeight", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders"))
                .andExpect(flash().attributeExists("errorMessage", "orderForm"));
    }

    @Test
    @DisplayName("画面: /orders POST は在庫不足メッセージを warningMessage で返す")
    void registerOrder_setsWarningMessage_whenBackordered() throws Exception {
        when(orderService.registerOrder(any())).thenReturn("在庫不足のため引当待ち（バックオーダー）で登録しました");

        mockMvc.perform(post("/orders")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES"))
                        .param("customerName", "顧客A")
                        .param("steelSheetId", "1")
                        .param("orderWeight", "1000")
                        .param("desiredDeliveryDate", LocalDate.now().plusDays(3).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders"))
                .andExpect(flash().attributeExists("warningMessage"));
    }

    @Test
    @DisplayName("画面: /arrivals は一覧表示に必要なモデルを設定する")
    void arrivals_setsModelAttributes() throws Exception {
        when(arrivalService.getSteelArrivalOptions()).thenReturn(List.of(
                new SteelArrivalOptionDto(1L, "STL-A", "鋼板A", new BigDecimal("1.00"), 1000, 0, 0)
        ));
        when(arrivalService.getRecentArrivals()).thenReturn(List.of(
                new ArrivalHistoryViewDto(LocalDateTime.now(), "CL-1", "STL-A", "鋼板A",
                        new BigDecimal("1.00"), 1000, 1000, 1000, null, false)
        ));
        when(arrivalService.buildDefaultForm(any())).thenReturn(
                new ArrivalForm(1L, "CL-NEW", 1000, LocalDate.now())
        );

        mockMvc.perform(get("/arrivals")
                        .with(SecurityMockMvcRequestPostProcessors.user("warehouse").roles("WAREHOUSE")))
                .andExpect(status().isOk())
                .andExpect(view().name("arrivals"))
                .andExpect(model().attributeExists("arrivalOptions", "histories", "arrivalForm"));
    }

    @Test
    @DisplayName("画面: /arrivals POST で業務エラー時は steelSheetId 付きで戻る")
    void registerArrival_redirectsWithSheetId_whenIllegalArgument() throws Exception {
        when(arrivalService.registerArrival(any())).thenThrow(new IllegalArgumentException("重複"));

        mockMvc.perform(post("/arrivals")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("warehouse").roles("WAREHOUSE"))
                        .param("steelSheetId", "5")
                        .param("coilNumber", "CL-DUP")
                        .param("arrivalWeight", "1000")
                        .param("arrivalDate", LocalDate.now().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/arrivals?steelSheetId=5"))
                .andExpect(flash().attributeExists("errorMessage", "arrivalForm"));
    }

    @Test
    @DisplayName("画面: /shipments は不正 status 指定でも all に正規化する")
    void shipments_normalizesUnknownStatusToAll() throws Exception {
        when(shipmentService.getShipmentOrders("all")).thenReturn(List.of());

        mockMvc.perform(get("/shipments")
                        .param("status", "INVALID")
                        .with(SecurityMockMvcRequestPostProcessors.user("warehouse").roles("WAREHOUSE")))
                .andExpect(status().isOk())
                .andExpect(view().name("shipments"))
                .andExpect(model().attribute("selectedStatus", "all"));
    }

    @Test
    @DisplayName("画面: /shipments/{id}/confirm は status パラメータを維持してリダイレクトする")
    void confirmShipment_redirectsWithStatusParameter() throws Exception {
        mockMvc.perform(post("/shipments/1/confirm")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("warehouse").roles("WAREHOUSE"))
                        .param("status", "shipped"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/shipments?status=shipped"));
    }

    @Test
    @DisplayName("画面: /shipments/{id}/mill-sheet.pdf は PDF ヘッダーを返す")
    void downloadMillSheet_returnsPdfResponse() throws Exception {
        when(reportService.generateMillSheetPdf(anyLong())).thenReturn("%PDF-test".getBytes());

        mockMvc.perform(get("/shipments/10/mill-sheet.pdf")
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("mill-sheet-10.pdf")));
    }

    @Test
    @DisplayName("画面: /shipments/export.csv は CSV を返す")
    void exportCsv_returnsCsvResponse() throws Exception {
        when(reportService.generateShipmentCsv()).thenReturn(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a'});

        mockMvc.perform(get("/shipments/export.csv")
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")));
    }

    @Test
    @DisplayName("セキュリティ: WAREHOUSE は /orders POST が禁止される")
    void warehouse_cannotPostOrders() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("warehouse").roles("WAREHOUSE"))
                        .param("customerName", "顧客")
                        .param("steelSheetId", "1")
                        .param("orderWeight", "1000")
                        .param("desiredDeliveryDate", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("セキュリティ: SALES は /arrivals POST が禁止される")
    void sales_cannotPostArrivals() throws Exception {
        mockMvc.perform(post("/arrivals")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES"))
                        .param("steelSheetId", "1")
                        .param("coilNumber", "CL-1")
                        .param("arrivalWeight", "1000")
                        .param("arrivalDate", LocalDate.now().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("セキュリティ: SALES は /shipments/{id}/confirm POST が禁止される")
    void sales_cannotConfirmShipment() throws Exception {
        mockMvc.perform(post("/shipments/1/confirm")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("セキュリティ: WAREHOUSE は /shipments/{id}/confirm POST が許可される")
    void warehouse_canConfirmShipment() throws Exception {
        mockMvc.perform(post("/shipments/1/confirm")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("warehouse").roles("WAREHOUSE")))
                .andExpect(status().is3xxRedirection());
    }
}
