package com.example.steelorder.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ArrivalHistoryViewDto(
        LocalDateTime createdAt,
        String coilNumber,
        String steelCode,
        String steelName,
        BigDecimal thickness,
        int width,
        int arrivalWeight,
        int currentAvailableWeight,
        String autoAllocatedInfo,
        boolean hasAutoAllocation
) {
}
