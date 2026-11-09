package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.response.IngredientDto;
import edu.fpt.sba.scms.service.IngredientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public List<IngredientDto> list(@RequestParam(required = false, defaultValue = "ACTIVE") String status) {
        return ingredientService.list(status);
    }
}
