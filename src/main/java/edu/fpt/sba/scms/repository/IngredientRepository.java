package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    List<Ingredient> findAllByStatusOrderByNameAsc(String status);

    List<Ingredient> findAllByOrderByNameAsc();

    List<Ingredient> findByNameContainingIgnoreCaseOrderByNameAsc(String keyword);

    List<Ingredient> findAllByStatusAndNameContainingIgnoreCaseOrderByNameAsc(String status, String keyword);

    Optional<Ingredient> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
