package com.example.steelorder.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record ArrivalForm(
        @NotNull(message = "鋼板を選択してください")
        Long steelSheetId,

        @NotBlank(message = "コイル番号を入力してください")
        @Pattern(regexp = ".*\\S.*", message = "コイル番号を入力してください")
        String coilNumber,

        @NotNull(message = "入荷重量を入力してください")
        @Min(value = 1, message = "入荷重量は1kg以上で入力してください")
        Integer arrivalWeight,

        @NotNull(message = "入荷日を入力してください")
        LocalDate arrivalDate
) {
}
