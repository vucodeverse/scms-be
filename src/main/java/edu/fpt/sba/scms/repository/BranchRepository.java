package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Integer>, JpaSpecificationExecutor<Branch> {

    Optional<Branch> findByCode(String code);

    boolean existsByCode(String code);
}
