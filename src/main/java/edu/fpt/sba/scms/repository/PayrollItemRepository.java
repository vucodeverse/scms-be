package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.PayrollItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollItemRepository extends JpaRepository<PayrollItem, Integer> {

    List<PayrollItem> findByPayPeriodId(Integer payPeriodId);

    Optional<PayrollItem> findByPayPeriodIdAndUserId(Integer payPeriodId, Integer userId);

    void deleteByPayPeriodId(Integer payPeriodId);
}

