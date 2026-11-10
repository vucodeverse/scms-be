package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.request.SaveToppingRequest;
import edu.fpt.sba.scms.dto.response.ToppingDto;
import edu.fpt.sba.scms.service.ToppingService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** UC11 — Toppings (IMPLEMENTATION.md §10.7). Đọc: COO + BM; ghi: COO. */
@RestController
@RequestMapping("/api/v1/toppings")
@RequiredArgsConstructor
@Tag(name = "08. Menu - Toppings", description = "Danh mục topping (UC11)")
@SecurityRequirement(name = "bearerAuth")
public class ToppingController {

    private final ToppingService toppingService;

    @Operation(summary = "Danh sách topping", description = "Không phân trang — FE dùng cho dropdown chọn topping.")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ToppingDto> list() {
        return toppingService.list();
    }

    @Operation(summary = "Tạo topping")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã tạo"),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_NAME",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COO')")
    public ToppingDto create(@RequestBody @Valid SaveToppingRequest request) {
        return toppingService.create(request);
    }

    @Operation(summary = "Cập nhật topping")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_NAME",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COO')")
    public ToppingDto update(@PathVariable Integer id, @RequestBody @Valid SaveToppingRequest request) {
        return toppingService.update(id, request);
    }

    @Operation(summary = "Xoá mềm topping", description = "Chuyển INACTIVE — topping ngừng bán, không hiện trên POS.")
    @ApiResponse(responseCode = "200", description = "OK")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('COO')")
    public ToppingDto softDelete(@PathVariable Integer id) {
        return toppingService.softDelete(id);
    }

    @Operation(summary = "Xoá cứng topping", description = "Gỡ topping khỏi mọi sản phẩm rồi xoá dòng.")
    @ApiResponse(responseCode = "204", description = "Đã xoá")
    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('COO')")
    public void hardDelete(@PathVariable Integer id) {
        toppingService.hardDelete(id);
    }
}
