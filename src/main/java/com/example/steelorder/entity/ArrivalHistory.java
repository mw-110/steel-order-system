package com.example.steelorder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "arrival_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArrivalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "arrival_id")
    private Long arrivalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coil_id", nullable = false)
    private Coil coil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "steel_sheet_id", nullable = false)
    private SteelSheet steelSheet;

    @Column(name = "arrival_weight", nullable = false)
    private Integer arrivalWeight;

    @Column(name = "arrival_date", nullable = false)
    private LocalDate arrivalDate;

    @Column(name = "auto_allocated_info")
    private String autoAllocatedInfo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
