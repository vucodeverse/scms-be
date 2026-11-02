package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.ProductTopping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductToppingRepository extends JpaRepository<ProductTopping, ProductTopping.Key> {

    List<ProductTopping> findAllByProductId(Integer productId);

    /** Một lần cho cả trang danh sách — tránh N+1 khi FE cần topping của từng sản phẩm. */
    List<ProductTopping> findAllByProductIdIn(List<Integer> productIds);

    void deleteAllByProductId(Integer productId);

    void deleteAllByToppingId(Integer toppingId);

    long countByToppingId(Integer toppingId);
}
