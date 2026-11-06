package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(Integer orderId);

    Optional<Payment> findFirstByReferenceCode(String referenceCode);

    Optional<Payment> findFirstBySepayRef(String sepayRef);
}

