package edu.fpt.sba.scms.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bảng {@code shift_assignment} (SDD 5.1.17, UC08, BR-15).
 * Phân công nhân viên vào ca làm việc theo lịch.
 */
@Entity
@Table(name = "shift_assignment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftAssignment {

    @EmbeddedId
    private ShiftAssignmentId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("shiftId")
    @JoinColumn(name = "shift_id")
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("employeeId")
    @JoinColumn(name = "employee_id")
    private User employee;

    @Column(name = "role_in_shift", length = 50)
    private String roleInShift;

    @Column(name = "is_replacement", nullable = false)
    @Builder.Default
    private Boolean isReplacement = false;
}

