package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.OrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderLineRepository extends JpaRepository<OrderLine, Integer> {

    List<OrderLine> findByOrderId(Integer orderId);

    boolean existsByProductId(Integer productId);
}
