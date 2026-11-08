package edu.fpt.sba.scms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ShiftAssignmentId implements Serializable {

    @Column(name = "shift_id")
    private Integer shiftId;

    @Column(name = "employee_id")
    private Integer employeeId;
}

