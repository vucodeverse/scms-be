package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Integer>, JpaSpecificationExecutor<Order> {

    Optional<Order> findById(Integer id);

    List<Order> findByStatus(OrderStatus status);
}
