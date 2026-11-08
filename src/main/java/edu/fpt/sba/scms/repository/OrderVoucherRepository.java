package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.OrderVoucher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderVoucherRepository extends JpaRepository<OrderVoucher, Integer> {

    Optional<OrderVoucher> findByOrderId(Integer orderId);

    boolean existsByOrderId(Integer orderId);

    void deleteByOrderId(Integer orderId);
}
