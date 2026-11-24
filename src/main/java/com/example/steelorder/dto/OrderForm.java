package com.example.steelorder.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record OrderForm(
        @NotBlank(message = "取引先名を入力してください")
        @Pattern(regexp = ".*\\S.*", message = "取引先名を入力してください")
        String customerName,

        @NotNull(message = "鋼板を選択してください")
        Long steelSheetId,

        @NotNull(message = "注文重量を入力してください")
        @Min(value = 1, message = "注文重量は1kg以上で入力してください")
        Integer orderWeight,

        @NotNull(message = "希望納期を入力してください")
        LocalDate desiredDeliveryDate
) {
}
