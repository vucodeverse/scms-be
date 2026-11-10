package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.PageResponse;
import edu.fpt.sba.scms.dto.request.OrderCategoryRequest;
import edu.fpt.sba.scms.dto.request.SaveCategoryRequest;
import edu.fpt.sba.scms.dto.request.SaveCategoryStatusRequest;
import edu.fpt.sba.scms.dto.request.query.CategoryListQuery;
import edu.fpt.sba.scms.dto.response.CategoryDetailDto;
import edu.fpt.sba.scms.dto.response.CategoryDto;
import edu.fpt.sba.scms.dto.response.MessageResponse;
import edu.fpt.sba.scms.service.CategoryService;
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

/**
 * UC09 — Menu Categories (IMPLEMENTATION.md §10.7). Đọc: COO + BM; ghi: COO.
 * Controller trả payload trần — envelope do {@code ApiResponseAdvice} bọc.
 */
@RestController
@RequestMapping("/api/v1/menu-categories")
@RequiredArgsConstructor
@Tag(name = "06. Menu - Categories", description = "Danh mục thực đơn (UC09)")
@SecurityRequirement(name = "bearerAuth")
public class MenuCategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "Danh sách danh mục (phân trang)",
            description = "Mặc định 10 phần tử/trang; lọc theo keyword (mã/tên) và status; sortBy: sortOrder|code|name|createdAt.")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<CategoryDto> list(@ModelAttribute @Valid CategoryListQuery query) {
        return PageResponse.of(categoryService.list(query));
    }

    @Operation(summary = "Chi tiết danh mục (kèm sản phẩm)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public CategoryDetailDto detail(@PathVariable Integer id) {
        return categoryService.detail(id);
    }

    @Operation(summary = "Tạo danh mục", description = "Mã danh mục do hệ thống sinh (CAT + id).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã tạo"),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_NAME",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COO')")
    public CategoryDto create(@RequestBody @Valid SaveCategoryRequest request) {
        return categoryService.create(request);
    }

    @Operation(summary = "Cập nhật danh mục")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_NAME | danh mục hệ thống",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COO')")
    public CategoryDto update(@PathVariable Integer id, @RequestBody @Valid SaveCategoryRequest request) {
        return categoryService.update(id, request);
    }

    @Operation(summary = "Đổi trạng thái danh mục",
            description = "Còn sản phẩm AVAILABLE/UNAVAILABLE → 422 CATEGORY_NOT_EMPTY.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "422", description = "CATEGORY_NOT_EMPTY",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('COO')")
    public CategoryDto changeStatus(@PathVariable Integer id,
                                    @RequestBody @Valid SaveCategoryStatusRequest request) {
        return categoryService.changeStatus(id, request);
    }

    @Operation(summary = "Sắp xếp lại danh mục",
            description = "Gửi sortOrder của toàn bộ danh mục đang hoạt động (trừ UNCAT).")
    @ApiResponse(responseCode = "200", description = "OK")
    @PutMapping("/sequence")
    @PreAuthorize("hasAuthority('COO')")
    public MessageResponse reorder(@RequestBody @Valid OrderCategoryRequest request) {
        categoryService.reorder(request);
        return new MessageResponse("Đã cập nhật thứ tự danh mục.");
    }

    @Operation(summary = "Xoá mềm danh mục", description = "Chuyển INACTIVE; điều kiện như PATCH status.")
    @ApiResponse(responseCode = "200", description = "OK")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('COO')")
    public CategoryDto softDelete(@PathVariable Integer id) {
        return categoryService.softDelete(id);
    }

    @Operation(summary = "Xoá cứng danh mục",
            description = "Chỉ khi danh mục không còn sản phẩm và không voucher nào tham chiếu; UNCAT không xoá được.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá"),
            @ApiResponse(responseCode = "409", description = "RESOURCE_CONFLICT — còn dữ liệu tham chiếu",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('COO')")
    public void hardDelete(@PathVariable Integer id) {
        categoryService.hardDelete(id);
    }
}
