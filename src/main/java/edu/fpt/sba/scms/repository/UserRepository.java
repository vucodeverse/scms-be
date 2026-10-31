package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    @Query("select u from User u join fetch u.role where u.username = :username")
    Optional<User> findWithRoleByUsername(@Param("username") String username);

    @Query("select u from User u join fetch u.role left join fetch u.branch where u.id = :id")
    Optional<User> findWithRoleAndBranchById(@Param("id") Integer id);

    @Query("select u from User u join fetch u.role left join fetch u.branch where u.username = :username")
    Optional<User> findWithRoleAndBranchByUsername(@Param("username") String username);

    boolean existsByUsername(String username);
}
