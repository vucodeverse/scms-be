package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.RefundStatus;
import edu.fpt.sba.scms.model.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Integer> {

    Optional<Refund> findFirstByOrderIdOrderByCreatedAtDesc(Integer orderId);

    boolean existsByOrderIdAndStatus(Integer orderId, RefundStatus status);

    Page<Refund> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

