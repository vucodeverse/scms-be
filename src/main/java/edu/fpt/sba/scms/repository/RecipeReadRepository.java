package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Đọc bảng {@code recipe} (UC12 — chỉ đọc). Bảng chưa có entity (phần ghi công thức thuộc M06 chưa triển khai);
 * môi trường thật cần migration tạo bảng trước khi dùng endpoint này.
 * <p>
 * Kế thừa repository của {@link Product} chỉ để dùng chung persistence context (không thêm hành vi).
 */
public interface RecipeReadRepository extends JpaRepository<Product, Integer> {

    /**
     * @param sizeName null = mọi dòng; ngược lại lấy dòng cùng size và dòng chung (size NULL)
     * @return mỗi dòng {@code [ingredient_id, quantity_base, size]}, sắp theo size rồi ingredient
     */
    @Query(value = """
            select r.ingredient_id, r.quantity_base, r.size
            from recipe r
            where r.product_id = :productId
              and (:sizeName is null or r.size is null or r.size = :sizeName)
            order by r.size, r.ingredient_id""", nativeQuery = true)
    List<Object[]> findRecipe(@Param("productId") Integer productId, @Param("sizeName") String sizeName);
}
