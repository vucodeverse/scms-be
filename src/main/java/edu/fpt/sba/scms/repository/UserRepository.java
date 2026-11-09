package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer>, JpaSpecificationExecutor<User> {

    @Query("select u from User u join fetch u.role left join fetch u.branch where u.username = :username")
    Optional<User> findWithRoleAndBranchByUsername(@Param("username") String username);

    Optional<User> findByUsername(String username);

    @Query("select u from User u join fetch u.role left join fetch u.branch where u.id = :id")
    Optional<User> findWithRoleAndBranchById(@Param("id") Integer id);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Integer id);

    /** Email không UNIQUE ở DB (có thể null) — chỉ cảnh báo trùng khi tạo/sửa. */
    Optional<User> findFirstByEmailIgnoreCase(String email);

    Optional<User> findFirstByEmailIgnoreCaseAndIdNot(String email, Integer id);

    /** PIN unique trong chi nhánh chỉ với user đang ACTIVE (index filtered — IMPLEMENTATION.md §7.3). */
    Optional<User> findFirstByBranchIdAndPinLookupHashAndStatus(
            Integer branchId, String pinLookupHash, UserStatus status);

    /** Thẻ unique khi ACTIVE; loại trừ chính user đang sửa. */
    Optional<User> findFirstByCardCodeAndStatusAndIdNot(
            String cardCode, UserStatus status, Integer id);

    long countByBranchIdAndStatus(Integer branchId, UserStatus status);

    /** Nhân sự ACTIVE của chi nhánh (trang chi tiết Branch) — join fetch role/branch để mapper không N+1. */
    @Query("""
            select u from User u join fetch u.role left join fetch u.branch
            where u.branchId = :branchId and u.status = :status
            order by u.fullName""")
    List<User> findAllByBranchIdAndStatus(@Param("branchId") Integer branchId, @Param("status") UserStatus status);

    /** Tất cả user giữ chức danh cụ thể đang gắn với chi nhánh (kể cả đã đổi trạng thái để đồng bộ 1-1). */
    List<User> findAllByBranchIdAndPosition(Integer branchId, Roles position);

    /** Khoá user khi đổi role/status để hai request đồng thời không cùng thoả "còn COO cuối" (tránh tự khoá hết). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockById(@Param("id") Integer id);

    @Query("select count(u) from User u join u.role r where r.code in :codes and u.status = :status")
    long countByRoleCodesAndStatus(@Param("codes") Collection<String> codes, @Param("status") UserStatus status);

    /** Manager của chi nhánh (để hiển thị managerName ở danh sách Branch) — join fetch role. */
    @Query("""
            select u from User u join fetch u.role
            where u.id in :ids""")
    List<User> findAllWithRoleByIdIn(@Param("ids") Collection<Integer> ids);
}
