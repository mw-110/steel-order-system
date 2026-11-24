package com.example.steelorder.dto;

import java.util.Locale;

public record DashboardAlertDto(
        String level,
        String message,
        String url,
        String buttonLabel
) {
    public String bootstrapClass() {
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "DANGER" -> "alert-danger";
            case "WARNING" -> "alert-warning";
            case "INFO" -> "alert-info";
            default -> "alert-secondary";
        };
    }

    public String badgeLabel() {
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "DANGER" -> "緊急";
            case "WARNING" -> "警告";
            case "INFO" -> "注意";
            default -> "その他";
        };
    }

    public String badgeClass() {
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "DANGER" -> "bg-danger";
            case "WARNING" -> "bg-warning text-dark";
            case "INFO" -> "bg-info text-dark";
            default -> "bg-secondary";
        };
    }
}
