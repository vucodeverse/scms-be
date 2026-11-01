package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    @Query("select u from User u join fetch u.role left join fetch u.branch where u.username = :username")
    Optional<User> findWithRoleAndBranchByUsername(@Param("username") String username);

    @Query("select u from User u join fetch u.role left join fetch u.branch where u.id = :id")
    Optional<User> findWithRoleAndBranchById(@Param("id") Integer id);

    boolean existsByUsername(String username);
}
