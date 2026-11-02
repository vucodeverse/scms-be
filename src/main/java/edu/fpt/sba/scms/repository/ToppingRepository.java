package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.model.Topping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ToppingRepository extends JpaRepository<Topping, Integer> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Integer id);

    List<Topping> findAllByOrderByNameAsc();

    /** Topping theo id và trạng thái (giao diện sản phẩm chỉ hiện topping ACTIVE). */
    List<Topping> findAllByIdInAndStatus(List<Integer> ids, RecordStatus status);
}
