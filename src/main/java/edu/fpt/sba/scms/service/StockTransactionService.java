package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.CreateDisposalSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateIssueSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateReceiveSlipRequest;
import edu.fpt.sba.scms.dto.response.StockDisposalSlipResponse;
import edu.fpt.sba.scms.dto.response.StockIssueSlipResponse;
import edu.fpt.sba.scms.dto.response.StockReceiveSlipResponse;

/**
 * Service quản lý các nghiệp vụ biến động kho (M09).
 */
public interface StockTransactionService {

    /**
     * Tạo phiếu nhập kho nguyên vật liệu từ NCC / điều chuyển (UC28).
     *
     * @param request thông tin phiếu nhập và danh sách các món nhận
     * @return chi tiết phiếu nhập kho và kết quả cập nhật tồn
     */
    StockReceiveSlipResponse receiveStock(CreateReceiveSlipRequest request);

    /**
     * Xuất kho nguyên vật liệu cho quầy Bar pha chế (UC29).
     *
     * @param request thông tin phiếu xuất và danh sách món cần xuất
     * @return chi tiết phiếu xuất kho và kết quả cập nhật tồn
     */
    StockIssueSlipResponse issueStockToBar(CreateIssueSlipRequest request);

    /**
     * Hủy nguyên vật liệu hỏng / hết hạn (UC30).
     *
     * @param request thông tin phiếu hủy và danh sách món hủy
     * @return chi tiết phiếu hủy kho và tổng thiệt hại tài chính
     */
    StockDisposalSlipResponse disposeStock(CreateDisposalSlipRequest request);
}
