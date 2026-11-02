package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.VoucherCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoucherCategoryRepository extends JpaRepository<VoucherCategory, VoucherCategory.Key> {

    List<VoucherCategory> findAllByVoucherId(Integer voucherId);

    void deleteAllByVoucherId(Integer voucherId);

    long countByCategoryId(Integer categoryId);
}
