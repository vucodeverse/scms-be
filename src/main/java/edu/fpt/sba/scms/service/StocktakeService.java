package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.CreateStocktakeSessionRequest;
import edu.fpt.sba.scms.dto.request.SubmitStocktakeCountsRequest;
import edu.fpt.sba.scms.dto.response.StocktakeReconciliationDto;
import edu.fpt.sba.scms.dto.response.StocktakeSessionDto;

public interface StocktakeService {

    /**
     * UC31: Khởi tạo đợt / phiên kiểm kê kho và snapshot số dư sổ sách tức thời.
     */
    StocktakeSessionDto createStocktakeSession(CreateStocktakeSessionRequest request);

    /**
     * UC31: Lấy thông tin phiên kiểm kê đang mở (status = OPEN) của chi nhánh.
     */
    StocktakeSessionDto getActiveStocktakeSession();

    /**
     * Tra cứu chi tiết một phiên kiểm kê theo ID thuộc chi nhánh.
     */
    StocktakeSessionDto getStocktakeSession(Long stocktakeId);

    /**
     * UC32: Nhập số đếm thực tế, tính toán chênh lệch, cân đè tồn kho thật và sinh giao dịch ADJUST.
     */
    StocktakeReconciliationDto adjustInventory(Long stocktakeId, SubmitStocktakeCountsRequest request);
}
