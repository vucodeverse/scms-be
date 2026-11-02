package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Integer>, JpaSpecificationExecutor<Product> {

    /**
     * Khoá dòng sản phẩm trong transaction ghi giá / topping / xoá — hai request đồng thời không vừa xoá vừa chèn.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Integer id);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Integer id);

    long countByCategoryId(Integer categoryId);

    /** Chặn ngừng danh mục còn sản phẩm đang bán. */
    boolean existsByCategoryIdAndStatusIn(Integer categoryId, Collection<ProductStatus> statuses);

    /** Đếm số sản phẩm theo danh mục cho cả trang: [categoryId, count]. */
    @Query("select p.categoryId, count(p) from Product p where p.categoryId in :categoryIds group by p.categoryId")
    List<Object[]> countByCategoryIds(@Param("categoryIds") List<Integer> categoryIds);

    List<Product> findAllByCategoryIdOrderByNameAsc(Integer categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.categoryId = :categoryId order by p.id")
    List<Product> lockAllByCategoryId(@Param("categoryId") Integer categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> lockAllByIdIn(@Param("ids") Collection<Integer> ids);

    /** Số voucher đang giới hạn phạm vi theo sản phẩm — chặn xoá cứng sản phẩm. */
    @Query("select count(vp) from VoucherProduct vp where vp.productId = :productId")
    long countVoucherLinks(@Param("productId") Integer productId);
}
