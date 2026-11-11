package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.request.CreateStocktakeSessionRequest;
import edu.fpt.sba.scms.dto.request.SubmitStocktakeCountsRequest;
import edu.fpt.sba.scms.dto.response.StocktakeReconciliationDto;
import edu.fpt.sba.scms.dto.response.StocktakeSessionDto;
import edu.fpt.sba.scms.service.StocktakeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

/**
 * Controller phục vụ chức năng kiểm kê kho và cân đối tồn kho (UC31, UC32).
 */
@RestController
@RequestMapping("/api/v1/stocktake")
@RequiredArgsConstructor
@Tag(name = "09. Inventory", description = "Quản lý kiểm kê kho & cân đối tồn (UC31, UC32)")
@SecurityRequirement(name = "bearerAuth")
public class StocktakeController {

    private final StocktakeService stocktakeService;

    @Operation(summary = "Khởi tạo đợt kiểm kê kho (FEA-05, UC31)",
            description = "Mở phiên kiểm kê kho mới, chụp ảnh số tồn sổ sách hiện thời (snapshot book_quantity) của toàn bộ nguyên liệu.")
    @ApiResponse(responseCode = "201", description = "Khởi tạo đợt kiểm kê thành công",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = StocktakeSessionDto.class)))
    @PostMapping("/session")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('BRANCH_MANAGER', 'COO')")
    public StocktakeSessionDto createStocktakeSession(
            @Parameter(description = "Mã chi nhánh (tùy chọn, mặc định theo token)")
            @RequestParam(value = "branchId", required = false) Integer branchId,
            @Valid @RequestBody(required = false) CreateStocktakeSessionRequest request) {
        return stocktakeService.createStocktakeSession(branchId, request);
    }

    @Operation(summary = "Lấy đợt kiểm kê đang mở - Đếm mù (FEA-05, UC31)",
            description = "Tra cứu đợt kiểm kê đang ở trạng thái OPEN của chi nhánh để nhập số đếm. Rule 1: ẩn bookQuantity = null chống gian lận.")
    @ApiResponse(responseCode = "200", description = "Lấy đợt kiểm kê đang mở thành công",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = StocktakeSessionDto.class)))
    @GetMapping("/active")
    @PreAuthorize("hasAnyAuthority('BRANCH_MANAGER', 'COO')")
    public StocktakeSessionDto getActiveStocktakeSession(
            @Parameter(description = "Mã chi nhánh (tùy chọn, mặc định theo token)")
            @RequestParam(value = "branchId", required = false) Integer branchId) {
        return stocktakeService.getActiveStocktakeSession(branchId);
    }

    @Operation(summary = "Tra cứu chi tiết đợt kiểm kê theo ID",
            description = "Lấy thông tin chi tiết phiên kiểm kê theo ID thuộc chi nhánh.")
    @ApiResponse(responseCode = "200", description = "Lấy thông tin đợt kiểm kê thành công",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = StocktakeSessionDto.class)))
    @GetMapping("/{stocktakeId}")
    @PreAuthorize("hasAnyAuthority('BRANCH_MANAGER', 'COO')")
    public StocktakeSessionDto getStocktakeSession(
            @Parameter(description = "ID đợt kiểm kê", example = "45")
            @PathVariable Long stocktakeId) {
        return stocktakeService.getStocktakeSession(stocktakeId);
    }

    @Operation(summary = "Nộp số đếm & Đối soát tự động 4 mắt (FEA-05)",
            description = "Quản lý nộp số đếm thực tế cùng Barista làm chứng. Bóc tách 3 cột đối chiếu. Nếu chênh lệch ròng <= 50.000 VNĐ tự động cân đối kho (ADJUST); nếu > 50.000 VNĐ khóa cân đối và sinh SecurityAlert(HIGH) lên COO.")
    @ApiResponse(responseCode = "200", description = "Đối soát kiểm kê thành công",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = StocktakeReconciliationDto.class)))
    @PostMapping("/{stocktakeId}/reconcile")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('BRANCH_MANAGER', 'COO')")
    public StocktakeReconciliationDto reconcileStocktake(
            @Parameter(description = "ID đợt kiểm kê", example = "45")
            @PathVariable Long stocktakeId,
            @Valid @RequestBody SubmitStocktakeCountsRequest request) {
        return stocktakeService.reconcileStocktake(stocktakeId, request);
    }

    @Operation(summary = "Cân đối chênh lệch sau kiểm kê (UC32)",
            description = "Nhập số đếm thực tế, so khớp variance = counted - book, ghi đè tồn kho thật và tự động sinh bản ghi ADJUST.")
    @ApiResponse(responseCode = "200", description = "Cân đối và đóng đợt kiểm kê thành công",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = StocktakeReconciliationDto.class)))
    @PostMapping("/{stocktakeId}/adjust")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('BRANCH_MANAGER', 'COO')")
    public StocktakeReconciliationDto adjustInventory(
            @Parameter(description = "ID đợt kiểm kê", example = "45")
            @PathVariable Long stocktakeId,
            @Valid @RequestBody SubmitStocktakeCountsRequest request) {
        return stocktakeService.adjustInventory(stocktakeId, request);
    }
}
