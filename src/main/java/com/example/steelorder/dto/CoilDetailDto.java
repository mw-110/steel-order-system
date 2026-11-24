package com.example.steelorder.dto;

import java.time.LocalDate;

public record CoilDetailDto(
        Long coilId,
        String coilNumber,
        LocalDate arrivalDate,
        int initialWeight,
        int allocatedWeight,
        int availableWeight,
        String statusLabel,
        String badgeClass,
        boolean isLongTermStagnant
) {
}
