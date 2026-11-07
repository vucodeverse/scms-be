package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    Optional<Order> findById(Integer id);
}

