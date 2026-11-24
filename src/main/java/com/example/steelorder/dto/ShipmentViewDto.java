package com.example.steelorder.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ShipmentViewDto(
        Long orderId,
        String orderNumber,
        LocalDate desiredDeliveryDate,
        String customerName,
        String steelCode,
        String steelName,
        String steelGrade,
        BigDecimal thickness,
        int width,
        int shipmentWeight,
        List<String> allocationDetails,
        String status,
        String statusLabel,
        String statusBadgeClass,
        LocalDateTime shippedAt,
        boolean overdue
) {
}
