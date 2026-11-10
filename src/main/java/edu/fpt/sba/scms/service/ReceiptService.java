package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.response.ReceiptResponse;
import edu.fpt.sba.scms.dto.response.report.ReportFileDto;
import edu.fpt.sba.scms.security.principal.UserPrincipal;

/**
 * Service quản lý hiển thị, kết xuất file và gửi lệnh in biên lai thanh toán (UC22, BR-31).
 */
public interface ReceiptService {

    /**
     * Lấy dữ liệu chi tiết hóa đơn/biên lai theo cấu trúc chuẩn để hiển thị giao diện POS (UC22).
     */
    ReceiptResponse getReceipt(Integer orderId, UserPrincipal principal);

    /**
     * Xuất biên lai thanh toán ra file PDF theo khổ giấy in nhiệt K80 chuẩn 80mm (UC22, BR-31).
     */
    ReportFileDto exportReceiptPdf(Integer orderId, UserPrincipal principal);

    /**
     * Gửi lệnh in (hoặc in lại) biên lai tới máy in nhiệt ESC/POS và ghi log kiểm toán (UC22, BR-31).
     */
    ReceiptResponse printReceipt(Integer orderId, UserPrincipal principal);
}

