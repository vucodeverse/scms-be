package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.PageResponse;
import edu.fpt.sba.scms.dto.request.AssignBranchManagerRequest;
import edu.fpt.sba.scms.dto.request.CreateBranchRequest;
import edu.fpt.sba.scms.dto.request.SaveBranchRequest;
import edu.fpt.sba.scms.dto.request.SaveBranchStatusRequest;
import edu.fpt.sba.scms.dto.request.query.BranchListQuery;
import edu.fpt.sba.scms.dto.response.BranchDetailDto;
import edu.fpt.sba.scms.dto.response.BranchDto;
import edu.fpt.sba.scms.service.BranchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** UC05 — Manage Branches (IMPLEMENTATION.md §10.3). Toàn bộ endpoint COO. */
@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
@Tag(name = "03. Branches", description = "Chi nhánh (UC05)")
@SecurityRequirement(name = "bearerAuth")
public class BranchController {

    private final BranchService branchService;

    @Operation(summary = "Danh sách chi nhánh (phân trang)",
            description = "Mặc định 10 phần tử/trang; sortBy: code|name|createdAt. posOnline = null khi chưa có heartbeat.")
    @GetMapping
    @PreAuthorize("permitAll()")
    public PageResponse<BranchDto> list(@ModelAttribute @Valid BranchListQuery query) {
        return PageResponse.of(branchService.list(query));
    }

    @Operation(summary = "Chi tiết chi nhánh (kèm nhân sự đang hoạt động)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COO')")
    public BranchDetailDto detail(@PathVariable Integer id) {
        return branchService.detail(id);
    }

    @Operation(summary = "Tạo chi nhánh")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã tạo"),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_CODE | RESOURCE_CONFLICT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COO')")
    public BranchDto create(@RequestBody @Valid CreateBranchRequest request) {
        return branchService.create(request);
    }

    @Operation(summary = "Cập nhật chi nhánh", description = "Mã chi nhánh read-only sau khi tạo.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COO')")
    public BranchDto update(@PathVariable Integer id, @RequestBody @Valid SaveBranchRequest request) {
        return branchService.update(id, request);
    }

    @Operation(summary = "Gắn / đổi / gỡ quản lý chi nhánh",
            description = "Chỉ gắn tài khoản BRANCH_MANAGER đang ACTIVE và chưa phụ trách chi nhánh nào khác. "
                    + "Truyền managerUserId = null để gỡ quản lý hiện tại.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "RESOURCE_CONFLICT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PutMapping("/{id}/manager")
    @PreAuthorize("hasAuthority('COO')")
    public BranchDto assignManager(@PathVariable Integer id,
                                   @RequestBody(required = false) @Valid AssignBranchManagerRequest request) {
        return branchService.assignManager(id, request == null ? new AssignBranchManagerRequest(null) : request);
    }

    @Operation(summary = "Đổi trạng thái chi nhánh",
            description = "Còn nhân sự ACTIVE khác Branch Manager → 422 BRANCH_NOT_EMPTY.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "422", description = "BRANCH_NOT_EMPTY",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('COO')")
    public BranchDto changeStatus(@PathVariable Integer id, @RequestBody @Valid SaveBranchStatusRequest request) {
        return branchService.changeStatus(id, request);
    }

    @Operation(summary = "Xoá mềm chi nhánh", description = "Chuyển INACTIVE (BR-13); điều kiện như PATCH status.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('COO')")
    public BranchDto softDelete(@PathVariable Integer id) {
        return branchService.softDelete(id);
    }
}
