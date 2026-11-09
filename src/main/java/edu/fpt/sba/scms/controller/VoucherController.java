package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.PageResponse;
import edu.fpt.sba.scms.dto.request.SaveVoucherRequest;
import edu.fpt.sba.scms.dto.request.SaveVoucherStatusRequest;
import edu.fpt.sba.scms.dto.request.query.VoucherListQuery;
import edu.fpt.sba.scms.dto.response.VoucherDetailDto;
import edu.fpt.sba.scms.dto.response.VoucherDto;
import edu.fpt.sba.scms.service.VoucherService;
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
 * UC13 — Vouchers (IMPLEMENTATION.md §10.8). Toàn bộ endpoint chỉ COO (đặc tả ghi Auth = COO).
 */
@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
@Tag(name = "09. Vouchers", description = "Quản lý voucher (UC13)")
@SecurityRequirement(name = "bearerAuth")
public class VoucherController {

    private final VoucherService voucherService;

    @Operation(summary = "Danh sách voucher (phân trang)",
            description = "Mặc định 10 phần tử/trang; lọc keyword/status; sortBy: code|startDate|endDate|createdAt.")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping
    @PreAuthorize("permitAll()")
    public PageResponse<VoucherDto> list(@ModelAttribute @Valid VoucherListQuery query) {
        return PageResponse.of(voucherService.list(query));
    }

    @Operation(summary = "Chi tiết voucher", description = "Kèm phạm vi áp dụng (sản phẩm, danh mục).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COO')")
    public VoucherDetailDto detail(@PathVariable Integer id) {
        return voucherService.detail(id);
    }

    @Operation(summary = "Tạo voucher")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã tạo"),
            @ApiResponse(responseCode = "400", description = "PERCENT > 100 | endDate ≤ startDate",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_CODE",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COO')")
    public VoucherDetailDto create(@RequestBody @Valid SaveVoucherRequest request) {
        return voucherService.create(request);
    }

    @Operation(summary = "Cập nhật voucher", description = "Mã voucher read-only; voucher đã dùng → 422 VOUCHER_IN_USE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "422", description = "VOUCHER_IN_USE",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COO')")
    public VoucherDetailDto update(@PathVariable Integer id, @RequestBody @Valid SaveVoucherRequest request) {
        return voucherService.update(id, request);
    }

    @Operation(summary = "Đổi trạng thái voucher")
    @ApiResponse(responseCode = "200", description = "OK")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('COO')")
    public VoucherDto changeStatus(@PathVariable Integer id,
                                   @RequestBody @Valid SaveVoucherStatusRequest request) {
        return voucherService.changeStatus(id, request);
    }

    @Operation(summary = "Xoá mềm voucher", description = "Chuyển INACTIVE.")
    @ApiResponse(responseCode = "200", description = "OK")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('COO')")
    public VoucherDto softDelete(@PathVariable Integer id) {
        return voucherService.softDelete(id);
    }

    @Operation(summary = "Xoá cứng voucher", description = "Chỉ khi voucher chưa từng được dùng ở đơn nào.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá"),
            @ApiResponse(responseCode = "422", description = "VOUCHER_IN_USE",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('COO')")
    public void hardDelete(@PathVariable Integer id) {
        voucherService.hardDelete(id);
    }
}
