package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.OrderItemTopping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemToppingRepository extends JpaRepository<OrderItemTopping, Integer> {

    List<OrderItemTopping> findByOrderLineId(Integer orderLineId);
}
