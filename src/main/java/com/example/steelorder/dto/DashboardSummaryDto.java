package com.example.steelorder.dto;

public record DashboardSummaryDto(
        long todayOrderCount,
        long backorderedCount,
        long allocatedCount
) {
}
