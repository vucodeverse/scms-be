package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.StocktakeLine;
import edu.fpt.sba.scms.model.StocktakeLineId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StocktakeLineRepository extends JpaRepository<StocktakeLine, StocktakeLineId> {

    List<StocktakeLine> findByStocktake_Id(Long stocktakeId);
}
