package com.api.crud.models.entity;

import com.api.crud.models.enums.PhysicalReturnStatus;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "physical_returns")
@Getter
@Setter
public class PhysicalReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true, updatable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhysicalReturnStatus status;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Long requestedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    private Long resolvedBy;
    private LocalDateTime resolvedAt;

    @Column(length = 500)
    private String resolutionReason;

    @ElementCollection
    @CollectionTable(
            name = "physical_return_lines",
            joinColumns = @JoinColumn(name = "return_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"return_id", "orderItemId"}))
    private List<PhysicalReturnLine> items = new ArrayList<>();
}
