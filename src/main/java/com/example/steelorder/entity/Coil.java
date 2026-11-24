package com.example.steelorder.entity;

import com.example.steelorder.entity.enums.CoilStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "coils")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Coil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coil_id")
    private Long coilId;

    @Column(name = "coil_number", nullable = false, unique = true, length = 30)
    private String coilNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "steel_sheet_id", nullable = false)
    private SteelSheet steelSheet;

    @Column(name = "initial_weight", nullable = false)
    private Integer initialWeight;

    @Column(name = "physical_weight", nullable = false)
    private Integer physicalWeight;

    @Column(name = "available_weight", nullable = false)
    private Integer availableWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CoilStatus status;

    @Column(name = "arrival_date", nullable = false)
    private LocalDate arrivalDate;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
