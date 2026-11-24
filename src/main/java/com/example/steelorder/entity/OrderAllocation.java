package com.example.steelorder.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allocation_id")
    private Long allocationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coil_id", nullable = false)
    private Coil coil;

    @Column(name = "allocated_weight", nullable = false)
    private Integer allocatedWeight;

    @Column(name = "allocated_at", nullable = false)
    private LocalDateTime allocatedAt;
}
