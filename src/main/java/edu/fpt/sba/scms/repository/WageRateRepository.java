package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.WageRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WageRateRepository extends JpaRepository<WageRate, Integer> {

    Optional<WageRate> findFirstByUserIdOrderByEffectiveFromDesc(Integer userId);
}

