package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.VoucherProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoucherProductRepository extends JpaRepository<VoucherProduct, VoucherProduct.Key> {

    List<VoucherProduct> findAllByVoucherId(Integer voucherId);

    void deleteAllByVoucherId(Integer voucherId);
}
