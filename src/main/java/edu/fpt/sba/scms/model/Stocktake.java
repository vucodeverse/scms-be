package edu.fpt.sba.scms.model;

import edu.fpt.sba.scms.common.enums.StocktakeStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity: Stocktake (Khớp SDD §5.1.13 — đúng 6 cột).
 * Quản lý phiên kiểm kê kho chi nhánh.
 */
@Entity
@Table(name = "stocktake", indexes = {
        @Index(name = "idx_stocktake_branch_status", columnList = "branch_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stocktake {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stocktake_id")
    private Long id; // 1. stocktake_id INT PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch; // 2. branch_id INT FK branch

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "started_by", nullable = false)
    private User startedBy; // 3. started_by INT FK user

    @Column(name = "started_at", nullable = false)
    private Instant startedAt; // 4. started_at TIMESTAMP

    @Column(name = "closed_at")
    private Instant closedAt; // 5. closed_at TIMESTAMP

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private StocktakeStatus status; // 6. status VARCHAR(20) NOT NULL (OPEN / ADJUSTED / CLOSED / CANCELLED / DISCREPANCY_ALERT)

    @OneToMany(mappedBy = "stocktake", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StocktakeLine> lines = new ArrayList<>();
}
