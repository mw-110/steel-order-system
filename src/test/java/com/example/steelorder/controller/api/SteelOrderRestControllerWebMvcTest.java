package com.example.steelorder.controller.api;

import com.example.steelorder.config.SecurityConfig;
import com.example.steelorder.dto.ArrivalForm;
import com.example.steelorder.dto.OrderForm;
import com.example.steelorder.service.ArrivalService;
import com.example.steelorder.service.CustomUserDetailsService;
import com.example.steelorder.service.InventoryService;
import com.example.steelorder.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SteelOrderRestController.class)
@Import(SecurityConfig.class)
class SteelOrderRestControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private OrderService orderService;

    @MockBean
    private ArrivalService arrivalService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("/api/v1/orders は不正な入力JSONを受けると400を返す")
    void registerOrder_returns400_whenValidationFails() throws Exception {
        OrderForm invalid = new OrderForm(" ", null, 0, null);

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("/api/v1/arrivals は不正な入力JSONを受けると400を返す")
    void registerArrival_returns400_whenValidationFails() throws Exception {
        ArrivalForm invalid = new ArrivalForm(null, "", 0, null);

        mockMvc.perform(post("/api/v1/arrivals")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("warehouse").roles("WAREHOUSE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("/api/v1/inventories は不正なクエリを受けると400を返す")
    void getInventories_returns400_whenValidationFails() throws Exception {
        mockMvc.perform(get("/api/v1/inventories")
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES"))
                        .param("minWidth", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("未認証で /api/v1/orders にPOSTするとログインへリダイレクトされる")
    void unauthorized_redirectsToLogin() throws Exception {
        OrderForm valid = new OrderForm("顧客A", 1L, 1000, LocalDate.now().plusDays(1));

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valid)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("SALES は /api/v1/orders へPOSTできる")
    void sales_canPostOrders() throws Exception {
        when(orderService.registerOrder(any())).thenReturn("ok");
        OrderForm valid = new OrderForm("顧客A", 1L, 1000, LocalDate.now().plusDays(1));

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valid)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("WAREHOUSE は /api/v1/orders へPOSTできず403になる")
    void warehouse_cannotPostOrders() throws Exception {
        OrderForm valid = new OrderForm("顧客A", 1L, 1000, LocalDate.now().plusDays(1));

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("warehouse").roles("WAREHOUSE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valid)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("WAREHOUSE は /api/v1/arrivals へPOSTできる")
    void warehouse_canPostArrivals() throws Exception {
        when(arrivalService.registerArrival(any())).thenReturn(
                new ArrivalService.ArrivalResult("ok", ArrivalService.ResultLevel.INFO));
        ArrivalForm valid = new ArrivalForm(1L, "CL-NEW-100", 1500, LocalDate.now());

        mockMvc.perform(post("/api/v1/arrivals")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("warehouse").roles("WAREHOUSE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valid)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SALES は /api/v1/arrivals へPOSTできず403になる")
    void sales_cannotPostArrivals() throws Exception {
        ArrivalForm valid = new ArrivalForm(1L, "CL-NEW-101", 1500, LocalDate.now());

        mockMvc.perform(post("/api/v1/arrivals")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valid)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN は /api/v1/orders と /api/v1/arrivals の両方へPOSTできる")
    void admin_canPostBothOrdersAndArrivals() throws Exception {
        when(orderService.registerOrder(any())).thenReturn("ok");
        when(arrivalService.registerArrival(any())).thenReturn(
                new ArrivalService.ArrivalResult("ok", ArrivalService.ResultLevel.SUCCESS));

        OrderForm order = new OrderForm("顧客A", 1L, 1000, LocalDate.now().plusDays(1));
        ArrivalForm arrival = new ArrivalForm(1L, "CL-NEW-102", 1500, LocalDate.now());

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/arrivals")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(arrival)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("認証済ユーザーは /api/v1/inventories を参照できる")
    void authenticatedUser_canGetInventories() throws Exception {
        when(inventoryService.search(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/inventories")
                        .with(SecurityMockMvcRequestPostProcessors.user("sales").roles("SALES")))
                .andExpect(status().isOk());
    }
}
