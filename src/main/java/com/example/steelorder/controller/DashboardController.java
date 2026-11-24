package com.example.steelorder.controller;

import com.example.steelorder.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("summary", dashboardService.getSummary());
        model.addAttribute("alerts", dashboardService.getAlerts());
        model.addAttribute("activePage", "dashboard");
        model.addAttribute("pageTitle", "ダッシュボード");
        return "dashboard";
    }
}
