package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.PinLockState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PinLockStateRepository extends JpaRepository<PinLockState, Integer> {

    Optional<PinLockState> findByLockKey(String lockKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PinLockState p WHERE p.lockKey = :lockKey")
    Optional<PinLockState> findByLockKeyForUpdate(@Param("lockKey") String lockKey);

    List<PinLockState> findAllByBranchIdAndLockedUntilAfter(Integer branchId, Instant now);

    List<PinLockState> findAllByLockedUntilAfter(Instant now);
}
