package com.example.steelorder.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "steel_sheets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SteelSheet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "steel_sheet_id")
    private Long steelSheetId;

    @Column(name = "steel_code", nullable = false, unique = true, length = 30)
    private String steelCode;

    @Column(name = "steel_name", nullable = false, length = 100)
    private String steelName;

    @Column(name = "steel_grade", nullable = false, length = 30)
    private String steelGrade;

    @Column(name = "usage_category", nullable = false, length = 50)
    private String usageCategory;

    @Column(name = "thickness", nullable = false, precision = 4, scale = 2)
    private BigDecimal thickness;

    @Column(name = "width", nullable = false)
    private Integer width;

    @Column(name = "safety_stock_weight", nullable = false)
    private Integer safetyStockWeight;
}
