package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.CashTransactionType;
import edu.fpt.sba.scms.model.CashTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CashTransactionRepository extends JpaRepository<CashTransaction, Integer> {

    List<CashTransaction> findByShiftIdOrderByCreatedAtDesc(Integer shiftId);

    List<CashTransaction> findByShiftIdAndType(Integer shiftId, CashTransactionType type);
}

