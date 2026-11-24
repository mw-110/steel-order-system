package com.example.steelorder.dto;

import java.math.BigDecimal;
import java.util.List;

public record SteelStockSummaryDto(
        Long steelSheetId,
        String steelCode,
        String steelName,
        String steelGrade,
        String usageCategory,
        BigDecimal thickness,
        int width,
        int safetyStockWeight,
        int totalPhysicalWeight,
        int totalAllocatedWeight,
        int totalAvailableWeight,
        String stockStatusLabel,
        String stockBadgeClass,
        List<CoilDetailDto> coils
) {
}
