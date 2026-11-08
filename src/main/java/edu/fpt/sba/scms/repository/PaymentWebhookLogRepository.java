package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.PaymentWebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookLogRepository extends JpaRepository<PaymentWebhookLog, Integer> {

    /** Kiểm tra xem mã giao dịch SePay đã từng được ghi nhận/xử lý chưa (chống trùng lặp theo BR-26). */
    boolean existsBySepayRef(String sepayRef);
}

