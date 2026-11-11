package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.request.ApproveRequisitionRequest;
import edu.fpt.sba.scms.dto.request.CreateDisposalSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateIssueSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateReceiveSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateRequisitionRequest;
import edu.fpt.sba.scms.dto.response.PendingRequisitionDto;
import edu.fpt.sba.scms.dto.response.StockDisposalSlipResponse;
import edu.fpt.sba.scms.dto.response.StockIssueSlipResponse;
import edu.fpt.sba.scms.dto.response.StockReceiveSlipResponse;
import edu.fpt.sba.scms.service.StockTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller phục vụ các giao dịch kho nguyên vật liệu (UC28, UC29, UC30)
 * và điều phối tiếp nhận hàng đa kho tổng FEA-01 Multi-Hub DC.
 */
@RestController
@RequestMapping("/api/v1/stock-transactions")
@RequiredArgsConstructor
@Tag(name = "09. Inventory", description = "Quản lý giao dịch kho nguyên vật liệu và điều phối đa kho tổng (FEA-01)")
@SecurityRequirement(name = "bearerAuth")
public class StockTransactionController {

    private final StockTransactionService stockTransactionService;

    @Operation(summary = "Quán lập đơn xin cấp hàng từ Kho Tổng (FEA-01 Bước 1)",
            description = "Quản lý chi nhánh gửi đề xuất xin cấp hàng theo số thùng chẵn (Rule 1).")
    @ApiResponse(responseCode = "201", description = "Tạo đơn đề xuất thành công")
    @PostMapping({"/requisition", "/requisitions"})
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('BRANCH_MANAGER', 'COO')")
    public PendingRequisitionDto createRequisition(@Valid @RequestBody CreateRequisitionRequest request) {
        return stockTransactionService.createRequisition(request);
    }

    @Operation(summary = "COO tra cứu danh sách đơn chờ duyệt phân bổ đa kho (FEA-01 Bước 2)",
            description = "Trả về danh sách đơn kèm tồn khả dụng của cả 3 Hub Kho Tổng Vùng.")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping({"/requisitions/pending", "/requisition/pending"})
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public List<PendingRequisitionDto> getPendingRequisitions() {
        return stockTransactionService.getPendingRequisitions();
    }

    @Operation(summary = "Tra cứu đơn xin cấp hàng theo trạng thái và chi nhánh",
            description = "Dùng cho quán kiểm tra các đơn đã duyệt để tiến hành nhận hàng.")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping({"/requisitions", "/requisition"})
    @PreAuthorize("isAuthenticated()")
    public List<PendingRequisitionDto> getRequisitions(
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false) Integer branchId) {
        return stockTransactionService.getRequisitions(status, branchId);
    }

    @Operation(summary = "COO duyệt phân bổ điều phối hàng đa kho tổng (FEA-01 Bước 3)",
            description = "Duyệt phân bổ số thùng xuất từ các Hub, lock và trừ tồn kho tổng, sinh StockTransaction ISSUE.")
    @ApiResponse(responseCode = "200", description = "Duyệt phân bổ điều phối thành công")
    @PostMapping({"/requisition/approve", "/requisitions/approve"})
    @PreAuthorize("hasAuthority('COO')")
    public PendingRequisitionDto approveRequisition(@Valid @RequestBody ApproveRequisitionRequest request) {
        return stockTransactionService.approveRequisition(request);
    }

    @Operation(summary = "COO từ chối đơn xin cấp hàng")
    @PostMapping({"/requisition/{id}/reject", "/requisitions/{id}/reject"})
    @PreAuthorize("hasAuthority('COO')")
    public void rejectRequisition(@PathVariable Long id, @RequestParam(required = false) String reason) {
        stockTransactionService.rejectRequisition(id, reason);
    }

    @Operation(summary = "Nhập kho nguyên vật liệu từ NCC hoặc Quán nhận hàng điều phối (UC28 & FEA-01 Bước 4)",
            description = "Tạo phiếu nhập kho GRN, kiểm tra trần Max Stock (Rule 3) và ghi nhận hàng bục vỡ (Rule 4).")
    @ApiResponse(responseCode = "201", description = "Nhập kho nguyên vật liệu thành công",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = StockReceiveSlipResponse.class)))
    @PostMapping("/receive")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('BRANCH_MANAGER', 'COO')")
    public StockReceiveSlipResponse receiveStock(@Valid @RequestBody CreateReceiveSlipRequest request) {
        return stockTransactionService.receiveStock(request);
    }

    @Operation(summary = "Xuất kho nội bộ ra quầy Bar (UC29 & FEA-02: Two-Party Transfer)",
            description = "Quản lý quán (BRANCH_MANAGER) xuất nguyên liệu cho Barista nhận ca (Rule 1). Khóa Pessimistic Lock (Rule 4), quy đổi đơn vị thùng/lẻ (Rule 2), chặn xuất âm (Rule 3), ghi vết PXK-BAR (Rule 5).")
    @ApiResponse(responseCode = "201", description = "Xuất kho cho quầy Bar thành công",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = StockIssueSlipResponse.class)))
    @PostMapping("/issue")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('BRANCH_MANAGER', 'COO')")
    public StockIssueSlipResponse issueStock(@Valid @RequestBody CreateIssueSlipRequest request) {
        return stockTransactionService.issueStockToBar(request);
    }

    @Operation(summary = "Quản lý xuất hủy hàng hỏng & hết date / Waste Ticket (FEA-04, UC30)",
            description = "Quản lý quán hoặc COO duyệt lập phiếu hủy nguyên vật liệu hỏng, rách bao bì, quá date hoặc sự cố quầy bar (Rule 1: bắt buộc ảnh, Rule 2: phân loại chuẩn, Rule 3: tính thiệt hại tài chính, Rule 4: ghi vết StockTransaction).")
    @ApiResponse(responseCode = "201", description = "Lập phiếu hủy nguyên vật liệu thành công",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = StockDisposalSlipResponse.class)))
    @PostMapping("/dispose")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('BRANCH_MANAGER', 'COO')")
    public StockDisposalSlipResponse disposeStock(@Valid @RequestBody CreateDisposalSlipRequest request) {
        return stockTransactionService.disposeStock(request);
    }
}
