package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.PaymentConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentConfigRepository extends JpaRepository<PaymentConfig, Integer> {

    /**
     * Lấy cấu hình đầu tiên của hệ thống (chuỗi dùng 1 cấu hình nhận tiền chung).
     */
    Optional<PaymentConfig> findFirstByOrderByIdAsc();
}

