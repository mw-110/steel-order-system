package com.example.steelorder.dto;

import java.time.LocalDate;

public record OrderAllocationDetailDto(
        String coilNumber,
        int allocatedWeight,
        LocalDate arrivalDate
) {
}
