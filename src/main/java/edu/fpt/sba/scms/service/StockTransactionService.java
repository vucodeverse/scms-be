package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.ApproveRequisitionRequest;
import edu.fpt.sba.scms.dto.request.CreateDisposalSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateIssueSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateReceiveSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateRequisitionRequest;
import edu.fpt.sba.scms.dto.response.PendingRequisitionDto;
import edu.fpt.sba.scms.dto.response.StockDisposalSlipResponse;
import edu.fpt.sba.scms.dto.response.StockIssueSlipResponse;
import edu.fpt.sba.scms.dto.response.StockReceiveSlipResponse;

import java.util.List;

/**
 * Service quản lý các nghiệp vụ biến động kho (M09, FEA-01 Multi-Hub DC).
 */
public interface StockTransactionService {

    /**
     * Tạo phiếu nhập kho nguyên vật liệu từ NCC / điều chuyển / tiếp nhận đơn điều phối kho tổng (UC28 & FEA-01).
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

    /**
     * Quán lập đơn xin cấp hàng từ Kho Tổng (FEA-01 Bước 1).
     *
     * @param request danh sách mặt hàng và số thùng/kiện chẵn cần xin
     * @return thông tin đơn xin cấp hàng đã lập kèm mã PR-YYYYMMDD-XXX
     */
    PendingRequisitionDto createRequisition(CreateRequisitionRequest request);

    /**
     * COO tra cứu danh sách đơn xin cấp hàng đang chờ duyệt phân bổ (FEA-01 Bước 2).
     *
     * @return danh sách đơn chờ duyệt kèm tồn khả dụng tại 3 Hub Kho Tổng
     */
    List<PendingRequisitionDto> getPendingRequisitions();

    /**
     * Tra cứu danh sách đơn đề xuất theo trạng thái và chi nhánh.
     *
     * @param status   PENDING / APPROVED / COMPLETED / ALL
     * @param branchId mã chi nhánh (nullable)
     * @return danh sách đơn đề xuất
     */
    List<PendingRequisitionDto> getRequisitions(String status, Integer branchId);

    /**
     * COO duyệt phân bổ điều phối hàng đa kho tổng (FEA-01 Bước 3).
     *
     * @param request thông tin duyệt phân bổ số thùng từ từng kho nguồn
     * @return thông tin đơn đã duyệt kèm các giao dịch xuất kho điều phối
     */
    PendingRequisitionDto approveRequisition(ApproveRequisitionRequest request);

    /**
     * COO từ chối cấp đơn đề xuất xin cấp hàng.
     */
    void rejectRequisition(Long requisitionId, String reason);
}
