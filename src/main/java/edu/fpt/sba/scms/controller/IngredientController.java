package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.request.CreateIngredientRequest;
import edu.fpt.sba.scms.dto.request.UpdateIngredientRequest;
import edu.fpt.sba.scms.dto.response.IngredientDto;
import edu.fpt.sba.scms.service.IngredientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ingredients")
@RequiredArgsConstructor
@Tag(name = "Nguyên vật liệu", description = "Quản lý nguyên vật liệu master kho tổng và công thức định lượng (BOM)")
@SecurityRequirement(name = "bearerAuth")
public class IngredientController {

    private final IngredientService ingredientService;

    @Operation(summary = "Danh sách nguyên vật liệu", description = "Tra cứu danh sách theo query param status (ACTIVE / INACTIVE / ALL)")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<IngredientDto> list(@RequestParam(required = false, defaultValue = "ACTIVE") String status) {
        return ingredientService.list(status);
    }

    @Operation(summary = "Chi tiết nguyên vật liệu")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public IngredientDto getById(@PathVariable Long id) {
        return ingredientService.getById(id);
    }

    @Operation(summary = "Tạo mặt hàng nguyên vật liệu mới (Dành riêng cho COO)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã tạo thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Không có quyền COO"),
            @ApiResponse(responseCode = "409", description = "Trùng tên nguyên vật liệu")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COO')")
    public IngredientDto create(@RequestBody @Valid CreateIngredientRequest request) {
        return ingredientService.create(request);
    }

    @Operation(summary = "Cập nhật thông tin mặt hàng nguyên vật liệu (Dành riêng cho COO)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Không có quyền COO"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nguyên vật liệu"),
            @ApiResponse(responseCode = "409", description = "Trùng tên nguyên vật liệu")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COO')")
    public IngredientDto update(@PathVariable Long id, @RequestBody @Valid UpdateIngredientRequest request) {
        return ingredientService.update(id, request);
    }

    @Operation(summary = "Bật/Tắt trạng thái hoạt động nguyên vật liệu (Dành riêng cho COO)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thay đổi trạng thái thành công"),
            @ApiResponse(responseCode = "403", description = "Không có quyền COO"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nguyên vật liệu")
    })
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('COO')")
    public IngredientDto toggleStatus(@PathVariable Long id,
                                      @RequestParam(required = false) String status) {
        return ingredientService.toggleStatus(id, status);
    }
}
