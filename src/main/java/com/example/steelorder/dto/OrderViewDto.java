package com.example.steelorder.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record OrderViewDto(
        Long orderId,
        String orderNumber,
        LocalDateTime orderedAt,
        String customerName,
        String steelName,
        String steelGrade,
        java.math.BigDecimal thickness,
        int width,
        int orderWeight,
        int allocatedWeight,
        LocalDate desiredDeliveryDate,
        String status,
        String statusLabel,
        String statusBadgeClass,
        List<OrderAllocationDetailDto> allocations
) {
}
