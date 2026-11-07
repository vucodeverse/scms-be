package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.request.CreateDisposalSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateIssueSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateReceiveSlipRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller phục vụ các giao dịch kho nguyên vật liệu (UC28, UC29, UC30).
 */
@RestController
@RequestMapping("/api/v1/stock-transactions")
@RequiredArgsConstructor
@Tag(name = "09. Inventory", description = "Quản lý giao dịch kho nguyên vật liệu (UC28, UC29, UC30)")
@SecurityRequirement(name = "bearerAuth")
public class StockTransactionController {

    private final StockTransactionService stockTransactionService;

    @Operation(summary = "Nhập kho nguyên vật liệu từ NCC / điều chuyển (UC28)",
            description = "Tạo phiếu nhập kho chính thức, tự động quy đổi kiện hàng lớn sang đơn vị cơ sở và cộng dồn tồn kho chi nhánh.")
    @ApiResponse(responseCode = "201", description = "Nhập kho nguyên vật liệu thành công",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = StockReceiveSlipResponse.class)))
    @PostMapping("/receive")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('BRANCH_MANAGER', 'COO')")
    public StockReceiveSlipResponse receiveStock(@Valid @RequestBody CreateReceiveSlipRequest request) {
        return stockTransactionService.receiveStock(request);
    }

    @Operation(summary = "Xuất kho nguyên vật liệu cho quầy Bar (UC29)",
            description = "Tạo phiếu xuất kho quầy bar, kiểm tra số tồn khả dụng bằng Pessimistic Lock, trừ tồn kho và tự động bật cờ cảnh báo thiếu hàng.")
    @ApiResponse(responseCode = "201", description = "Xuất kho cho quầy Bar thành công",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = StockIssueSlipResponse.class)))
    @PostMapping("/issue")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('BRANCH_MANAGER', 'COO')")
    public StockIssueSlipResponse issueStock(@Valid @RequestBody CreateIssueSlipRequest request) {
        return stockTransactionService.issueStockToBar(request);
    }

    @Operation(summary = "Hủy nguyên vật liệu hỏng / hết hạn (UC30)",
            description = "Lập phiếu hủy kho cho nguyên vật liệu hỏng, rách bao bì, quá hạn sử dụng, trừ tồn kho và tính tổng thiệt hại tài chính.")
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
