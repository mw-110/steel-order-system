package com.example.steelorder.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record InventorySearchForm(
        String steelGrade,
        String usageCategory,
        @DecimalMin(value = "0.00", message = "最小板厚は0以上で入力してください")
        BigDecimal minThickness,
        @DecimalMin(value = "0.00", message = "最大板厚は0以上で入力してください")
        BigDecimal maxThickness,
        @Positive(message = "最小幅は1以上で入力してください")
        Integer minWidth,
        @Positive(message = "最大幅は1以上で入力してください")
        Integer maxWidth
) {
}
