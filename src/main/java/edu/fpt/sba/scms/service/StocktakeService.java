package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.CreateStocktakeSessionRequest;
import edu.fpt.sba.scms.dto.request.SubmitStocktakeCountsRequest;
import edu.fpt.sba.scms.dto.response.StocktakeReconciliationDto;
import edu.fpt.sba.scms.dto.response.StocktakeSessionDto;

public interface StocktakeService {

    /**
     * UC31 & FEA-05: Khởi tạo đợt / phiên kiểm kê kho và snapshot số dư sổ sách tức thời.
     */
    StocktakeSessionDto createStocktakeSession(CreateStocktakeSessionRequest request);

    StocktakeSessionDto createStocktakeSession(Integer branchId, CreateStocktakeSessionRequest request);

    /**
     * UC31 & FEA-05: Lấy thông tin phiên kiểm kê đang mở (status = OPEN) của chi nhánh.
     * Rule 1: Trả về bookQuantity = null để phục vụ đếm mù.
     */
    StocktakeSessionDto getActiveStocktakeSession();

    StocktakeSessionDto getActiveStocktakeSession(Integer branchId);

    /**
     * Tra cứu chi tiết một phiên kiểm kê theo ID thuộc chi nhánh.
     */
    StocktakeSessionDto getStocktakeSession(Long stocktakeId);

    /**
     * FEA-05: Nộp số đếm thực tế 4 mắt & đối soát tự động.
     * - Rule 2: Bắt buộc có witnessName.
     * - Rule 4: Nếu |netVarianceValue| <= 50.000 VNĐ -> Cân đối kho (ADJUST).
     *           Nếu |netVarianceValue| > 50.000 VNĐ -> Khóa cân đối, chuyển DISCREPANCY_ALERT, sinh SecurityAlert(HIGH).
     */
    StocktakeReconciliationDto reconcileStocktake(Long stocktakeId, SubmitStocktakeCountsRequest request);

    /**
     * UC32: Nhập số đếm thực tế, tính toán chênh lệch, cân đè tồn kho thật và sinh giao dịch ADJUST.
     */
    StocktakeReconciliationDto adjustInventory(Long stocktakeId, SubmitStocktakeCountsRequest request);
}
