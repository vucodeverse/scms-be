package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.PayPeriod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayPeriodRepository extends JpaRepository<PayPeriod, Integer> {

    List<PayPeriod> findByBranchIdOrderByStartDateDesc(Integer branchId);

    Page<PayPeriod> findByBranchIdOrderByStartDateDesc(Integer branchId, Pageable pageable);
}

