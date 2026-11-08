package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.CreateRefundRequest;
import edu.fpt.sba.scms.dto.response.RefundResponse;
import edu.fpt.sba.scms.security.principal.UserPrincipal;

/**
 * Xử lý hoàn tiền cho đơn hàng đã thanh toán (UC23, BR-32/33/34/35).
 */
public interface RefundService {

    RefundResponse processRefund(CreateRefundRequest request, UserPrincipal principal);
}

