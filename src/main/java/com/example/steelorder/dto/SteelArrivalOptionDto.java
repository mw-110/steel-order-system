package com.example.steelorder.dto;

import java.math.BigDecimal;

public record SteelArrivalOptionDto(
        Long steelSheetId,
        String steelCode,
        String steelName,
        BigDecimal thickness,
        int width,
        long backorderCount,
        int shortageWeight
) {
}
