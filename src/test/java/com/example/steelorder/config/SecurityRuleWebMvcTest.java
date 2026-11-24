package com.example.steelorder.config;

import com.example.steelorder.controller.ArrivalController;
import com.example.steelorder.controller.DashboardController;
import com.example.steelorder.controller.InventoryController;
import com.example.steelorder.controller.LoginController;
import com.example.steelorder.controller.OrderController;
import com.example.steelorder.controller.ShipmentController;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        LoginController.class,
        DashboardController.class,
        InventoryController.class,
        ArrivalController.class,
        OrderController.class,
        ShipmentController.class
})
@Import(SecurityConfig.class)
class SecurityRuleWebMvcTest {

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
    @DisplayName("セキュリティ: /login は匿名アクセス可")
    void login_isPermitted() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("セキュリティ: /orders POST は SALES/ADMIN のみ許可")
    void ordersPost_isAllowedOnlyForSalesOrAdmin() throws Exception {
        when(orderService.registerOrder(any())).thenReturn("ok");

        mockMvc.perform(post("/orders")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES"))
                        .param("customerName", "顧客")
                        .param("steelSheetId", "1")
                        .param("orderWeight", "1000")
                        .param("desiredDeliveryDate", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().is3xxRedirection());

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
    @DisplayName("セキュリティ: /arrivals と /shipments POST は WAREHOUSE/ADMIN のみ許可")
    void warehouseEndpointsPost_areAllowedOnlyForWarehouseOrAdmin() throws Exception {
        when(arrivalService.registerArrival(any())).thenReturn(
            new ArrivalService.ArrivalResult("ok", ArrivalService.ResultLevel.INFO)
        );

        mockMvc.perform(post("/arrivals")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("warehouse").roles("WAREHOUSE"))
                        .param("steelSheetId", "1")
                        .param("coilNumber", "CL-1")
                        .param("arrivalWeight", "1000")
                        .param("arrivalDate", LocalDate.now().toString()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/shipments/1/confirm")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("warehouse").roles("WAREHOUSE")))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/arrivals")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES"))
                        .param("steelSheetId", "1")
                        .param("coilNumber", "CL-1")
                        .param("arrivalWeight", "1000")
                        .param("arrivalDate", LocalDate.now().toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/shipments/1/confirm")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("セキュリティ: GET系は認証済みで許可、未認証はリダイレクト")
    void getEndpoints_requireAuthentication() throws Exception {
        mockMvc.perform(get("/inventories"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/inventories")
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/shipments")
                        .with(SecurityMockMvcRequestPostProcessors.user("warehouse").roles("WAREHOUSE")))
                .andExpect(status().isOk());
    }
}
