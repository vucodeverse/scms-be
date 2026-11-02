package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.model.ProductSize;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductSizeRepository extends JpaRepository<ProductSize, Integer> {

    List<ProductSize> findAllByProductIdOrderBySizeAsc(Integer productId);

    List<ProductSize> findAllByProductIdIn(List<Integer> productIds);

    Optional<ProductSize> findByProductIdAndSize(Integer productId, ProductSizeName size);

    void deleteAllByProductId(Integer productId);
}
