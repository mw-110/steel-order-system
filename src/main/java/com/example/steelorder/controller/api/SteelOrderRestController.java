package com.example.steelorder.controller.api;

import com.example.steelorder.dto.ArrivalForm;
import com.example.steelorder.dto.InventorySearchForm;
import com.example.steelorder.dto.OrderForm;
import com.example.steelorder.dto.OrderViewDto;
import com.example.steelorder.dto.SteelStockSummaryDto;
import com.example.steelorder.service.ArrivalService;
import com.example.steelorder.service.InventoryService;
import com.example.steelorder.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "外部連携API", description = "鋼板受発注・在庫連携用REST API")
public class SteelOrderRestController {

    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final ArrivalService arrivalService;

    public SteelOrderRestController(InventoryService inventoryService,
                                    OrderService orderService,
                                    ArrivalService arrivalService) {
        this.inventoryService = inventoryService;
        this.orderService = orderService;
        this.arrivalService = arrivalService;
    }

    @GetMapping("/inventories")
    @Operation(summary = "鋼板別在庫サマリーおよびコイル明細一覧の取得")
    public List<SteelStockSummaryDto> getInventories(@Valid @ModelAttribute InventorySearchForm form) {
        return inventoryService.search(form);
    }

    @GetMapping("/orders")
    @Operation(summary = "全注文ステータスおよび引当明細一覧の取得")
    public List<OrderViewDto> getOrders() {
        return orderService.findAllOrders();
    }

    @PostMapping("/orders")
    @Operation(summary = "新規注文登録およびFIFO自動在庫引当の実行")
    public ApiMessage registerOrder(@RequestBody @Valid OrderForm form) {
        return new ApiMessage(orderService.registerOrder(form));
    }

    @PostMapping("/arrivals")
    @Operation(summary = "新規コイル入荷登録およびバックオーダー自動充当の実行")
    public ApiMessage registerArrival(@RequestBody @Valid ArrivalForm form) {
        ArrivalService.ArrivalResult result = arrivalService.registerArrival(form);
        return new ApiMessage(result.message());
    }

    public record ApiMessage(String message) {
    }
}
