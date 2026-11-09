package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.Category;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Integer>, JpaSpecificationExecutor<Category> {

    Optional<Category> findByCode(String code);

    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Integer id);

    /**
     * Khoá các danh mục cần ghi, sắp theo id để mọi transaction khoá cùng thứ tự (không deadlock).
     * Trả về dòng còn tồn tại; id không có sẽ không xuất hiện trong kết quả.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c where c.id in :ids order by c.id")
    List<Category> lockAllByIdIn(Collection<Integer> ids);

    /** Số voucher đang giới hạn phạm vi theo danh mục — chặn xoá cứng danh mục. */
    @Query("select count(vc) from VoucherCategory vc where vc.categoryId = :categoryId")
    long countVoucherLinks(@Param("categoryId") Integer categoryId);
}
