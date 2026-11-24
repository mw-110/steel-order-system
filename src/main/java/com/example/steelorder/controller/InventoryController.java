package com.example.steelorder.controller;

import com.example.steelorder.dto.InventorySearchForm;
import com.example.steelorder.service.InventoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/inventories")
    public String inventories(@ModelAttribute("searchForm") InventorySearchForm searchForm,
                              Model model) {
        model.addAttribute("stocks", inventoryService.search(searchForm));
        model.addAttribute("activePage", "inventories");
        model.addAttribute("pageTitle", "在庫照会・検索");
        return "inventories";
    }
}
