package com.example.steelorder.controller;

import com.example.steelorder.service.ReportService;
import com.example.steelorder.service.ShipmentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Controller
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final ReportService reportService;

    public ShipmentController(ShipmentService shipmentService,
                              ReportService reportService) {
        this.shipmentService = shipmentService;
        this.reportService = reportService;
    }

    @GetMapping("/shipments")
    public String shipments(@RequestParam(name = "status", required = false) String status,
                            Model model) {
        String selectedStatus = normalizeStatus(status);

        model.addAttribute("shipments", shipmentService.getShipmentOrders(selectedStatus));
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("activePage", "shipments");
        model.addAttribute("pageTitle", "出荷管理・帳票出力");
        return "shipments";
    }

    @PostMapping("/shipments/{id}/confirm")
    public String confirmShipment(@PathVariable("id") Long orderId,
                                  @RequestParam(name = "status", required = false) String status,
                                  RedirectAttributes redirectAttributes) {
        try {
            shipmentService.confirmShipment(orderId);
            redirectAttributes.addFlashAttribute("successMessage", "出荷を確定しました");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return buildRedirectUrl(status);
    }

    @GetMapping("/shipments/{id}/mill-sheet.pdf")
    public ResponseEntity<byte[]> downloadMillSheet(@PathVariable("id") Long orderId) {
        byte[] pdf = reportService.generateMillSheetPdf(orderId);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename("mill-sheet-" + orderId + ".pdf", StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf);
    }

    @GetMapping("/shipments/export.csv")
    public ResponseEntity<byte[]> exportCsv() {
        byte[] csv = reportService.generateShipmentCsv();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("shipment-results.csv", StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(csv);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "all";
        }

        String normalized = status.trim().toLowerCase();
        if ("allocated".equals(normalized) || "shipped".equals(normalized)) {
            return normalized;
        }
        return "all";
    }

    private String buildRedirectUrl(String status) {
        String normalized = normalizeStatus(status);
        if ("all".equals(normalized)) {
            return "redirect:/shipments";
        }
        return "redirect:/shipments?status=" + normalized;
    }
}
