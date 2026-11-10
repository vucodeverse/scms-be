package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.ShiftHandoverStatus;
import edu.fpt.sba.scms.common.enums.ShiftHandoverType;
import edu.fpt.sba.scms.model.ShiftHandover;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShiftHandoverRepository extends JpaRepository<ShiftHandover, Integer> {

    List<ShiftHandover> findByShiftIdOrderByCreatedAtDescIdDesc(Integer shiftId);

    Optional<ShiftHandover> findFirstByShiftIdAndStatusOrderByIdDesc(Integer shiftId, ShiftHandoverStatus status);

    Optional<ShiftHandover> findFirstByShiftIdAndStatusAndHandoverTypeOrderByIdDesc(
            Integer shiftId, ShiftHandoverStatus status, ShiftHandoverType handoverType);

    List<ShiftHandover> findByShiftIdInAndStatus(Collection<Integer> shiftIds, ShiftHandoverStatus status);
}
