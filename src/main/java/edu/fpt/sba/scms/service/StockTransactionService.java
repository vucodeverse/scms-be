package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.CreateReceiveSlipRequest;
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
}
