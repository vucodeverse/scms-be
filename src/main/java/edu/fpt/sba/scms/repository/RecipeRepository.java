package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Integer> {

    List<Recipe> findByProductIdOrderBySizeAscIngredientIdAsc(Integer productId);

    List<Recipe> findByProductIdAndSizeOrderByIngredientIdAsc(Integer productId, ProductSizeName size);

    List<Recipe> findByProductIdAndSizeIsNullOrderByIngredientIdAsc(Integer productId);

    @Query("""
            select r from Recipe r
            where r.productId = :productId
              and (:size is null or r.size is null or r.size = :size)
            order by r.size, r.ingredientId""")
    List<Recipe> findApplicableRecipes(@Param("productId") Integer productId, @Param("size") ProductSizeName size);

    @Modifying
    @Query("delete from Recipe r where r.productId = :productId")
    void deleteByProductId(@Param("productId") Integer productId);

    @Modifying
    @Query("delete from Recipe r where r.productId = :productId and r.size = :size")
    void deleteByProductIdAndSize(@Param("productId") Integer productId, @Param("size") ProductSizeName size);

    @Modifying
    @Query("delete from Recipe r where r.productId = :productId and r.size is null")
    void deleteByProductIdAndSizeIsNull(@Param("productId") Integer productId);

    boolean existsByIngredientId(Integer ingredientId);

    boolean existsByProductId(Integer productId);
}

