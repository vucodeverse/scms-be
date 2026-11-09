package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.request.SaveIngredientRequest;
import edu.fpt.sba.scms.dto.response.IngredientDto;
import edu.fpt.sba.scms.service.IngredientService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Nguyên vật liệu", description = "Quản lý nguyên vật liệu và công thức định lượng (BOM)")
public class IngredientController {

    private final IngredientService ingredientService;

    @Operation(summary = "Danh sách nguyên vật liệu")
    @GetMapping
    @PreAuthorize("permitAll()")
    public List<IngredientDto> list(
            @RequestParam(required = false, defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String keyword) {
        return ingredientService.list(status, keyword);
    }

    @Operation(summary = "Chi tiết nguyên vật liệu")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public IngredientDto getById(@PathVariable Long id) {
        return ingredientService.getById(id);
    }

    @Operation(summary = "Tạo mới nguyên vật liệu")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public IngredientDto create(@RequestBody @Valid SaveIngredientRequest request) {
        return ingredientService.create(request);
    }

    @Operation(summary = "Cập nhật nguyên vật liệu")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public IngredientDto update(@PathVariable Long id, @RequestBody @Valid SaveIngredientRequest request) {
        return ingredientService.update(id, request);
    }

    @Operation(summary = "Đổi trạng thái nguyên vật liệu (ACTIVE / INACTIVE)")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public IngredientDto changeStatus(@PathVariable Long id, @RequestParam(defaultValue = "INACTIVE") String status) {
        return ingredientService.changeStatus(id, status);
    }
}
