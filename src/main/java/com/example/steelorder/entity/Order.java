package com.example.steelorder.entity;

import com.example.steelorder.entity.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_number", nullable = false, unique = true, length = 30)
    private String orderNumber;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "steel_sheet_id", nullable = false)
    private SteelSheet steelSheet;

    @Column(name = "order_weight", nullable = false)
    private Integer orderWeight;

    @Column(name = "allocated_weight", nullable = false)
    private Integer allocatedWeight;

    @Column(name = "desired_delivery_date", nullable = false)
    private LocalDate desiredDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;
}
