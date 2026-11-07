package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.SortDirection;
import edu.fpt.sba.scms.dto.PageResponse;
import edu.fpt.sba.scms.dto.request.query.InventoryQuery;
import edu.fpt.sba.scms.dto.response.IngredientStockDto;
import edu.fpt.sba.scms.dto.response.InventoryPageResponse;
import edu.fpt.sba.scms.dto.response.LowStockAlertDto;
import edu.fpt.sba.scms.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller phục vụ tra cứu sổ kho & cảnh báo thiếu hàng (UC33).
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "09. Inventory", description = "Quản lý sổ kho & cảnh báo tồn thấp (UC33)")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "Tra cứu sổ kho chi nhánh (UC33 §4.1)",
            description = "Lấy danh sách tồn kho phân trang, tìm kiếm theo tên và lọc theo trạng thái tồn (ALL, LOW_STOCK, SUFFICIENT).")
    @ApiResponse(responseCode = "200", description = "Lấy danh sách tồn kho chi nhánh thành công",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = InventoryPageResponse.class)))
    @GetMapping
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public PageResponse<IngredientStockDto> getBranchInventory(
            @Parameter(description = "Mã chi nhánh (chỉ áp dụng cho quyền COO, bỏ trống sẽ lấy chi nhánh mặc định)")
            @RequestParam(required = false) Long branchId,

            @Parameter(description = "Tìm theo tên nguyên liệu", example = "Cà phê")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Bộ lọc trạng thái tồn: ALL (tất cả), LOW_STOCK (thiếu hàng), SUFFICIENT (đủ hàng)",
                    schema = @Schema(allowableValues = {"ALL", "LOW_STOCK", "SUFFICIENT"}, defaultValue = "ALL"))
            @RequestParam(required = false, defaultValue = "ALL") String status,

            @Parameter(description = "Chỉ số trang bắt đầu từ 0", example = "0")
            @RequestParam(required = false, defaultValue = "0") Integer page,

            @Parameter(description = "Số lượng bản ghi trên một trang", example = "20")
            @RequestParam(required = false, defaultValue = "20") Integer size,

            @Parameter(description = "Trường sắp xếp (mặc định ingredient.name)", example = "ingredient.name")
            @RequestParam(required = false, defaultValue = "ingredient.name") String sortBy,

            @Parameter(description = "Thứ tự sắp xếp: ASC | DESC",
                    schema = @Schema(allowableValues = {"ASC", "DESC"}, defaultValue = "ASC"))
            @RequestParam(required = false, defaultValue = "ASC") SortDirection sortDir) {

        InventoryQuery query = new InventoryQuery(branchId, keyword, status, page, size, sortBy, sortDir);
        return inventoryService.getBranchInventory(query);
    }

    @Operation(summary = "Lấy danh sách cảnh báo tồn thấp (UC33 §4.2)",
            description = "Hiển thị danh sách các nguyên vật liệu có nguy cơ đứt hàng (tồn <= định mức an toàn) và gợi ý số kiện đặt bù.")
    @ApiResponse(responseCode = "200", description = "Lấy danh sách nguyên vật liệu chạm ngưỡng an toàn thành công",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = LowStockAlertDto.class))))
    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public List<LowStockAlertDto> getLowStockAlerts(
            @Parameter(description = "Mã chi nhánh (chỉ áp dụng cho quyền COO)")
            @RequestParam(required = false) Integer branchId) {
        return inventoryService.getLowStockAlerts(branchId);
    }
}
