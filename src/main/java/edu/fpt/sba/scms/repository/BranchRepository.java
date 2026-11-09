package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.Branch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Integer>, JpaSpecificationExecutor<Branch> {

    Optional<Branch> findByCode(String code);

    boolean existsByCode(String code);

    /** Khoá bản ghi chi nhánh khi gán/đổi/gỡ quản lý để giữ ràng buộc 1 chi nhánh - 1 quản lý chính. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Branch b where b.id = :id")
    Optional<Branch> lockById(@Param("id") Integer id);
}
