package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.PageResponse;
import edu.fpt.sba.scms.dto.request.SaveProductRecipeRequest;
import edu.fpt.sba.scms.dto.request.SaveProductRequest;
import edu.fpt.sba.scms.dto.request.SaveProductSizeRequest;
import edu.fpt.sba.scms.dto.request.SaveProductToppingsRequest;
import edu.fpt.sba.scms.dto.request.query.ProductListQuery;
import edu.fpt.sba.scms.dto.request.query.RecipeProductQuery;
import edu.fpt.sba.scms.dto.response.ProductDetailDto;
import edu.fpt.sba.scms.dto.response.ProductDto;
import edu.fpt.sba.scms.dto.response.ProductRecipeResponse;
import edu.fpt.sba.scms.dto.response.ProductSizeDto;
import edu.fpt.sba.scms.dto.response.RecipeLineDto;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.ProductService;
import edu.fpt.sba.scms.service.ProductSizeService;
import edu.fpt.sba.scms.service.RecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * UC10/UC11 (+UC12 chỉ đọc) — Products, giá size, topping, công thức (IMPLEMENTATION.md §10.7).
 * Đọc: COO + BM; ghi: COO.
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "07. Menu - Products", description = "Sản phẩm, giá size, topping, công thức (UC10/UC11/UC12)")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

    private final ProductService productService;
    private final ProductSizeService productSizeService;
    private final RecipeService recipeService;

    @Operation(summary = "Danh sách sản phẩm (phân trang)",
            description = "Mặc định 10 phần tử/trang; lọc keyword/categoryId/status; sortBy: name|basePrice|createdAt.")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<ProductDto> list(@ModelAttribute @Valid ProductListQuery query) {
        return PageResponse.of(productService.list(query));
    }

    @Operation(summary = "Chi tiết sản phẩm", description = "Kèm bảng giá size, topping và công thức.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public ProductDetailDto detail(@PathVariable Integer id) {
        return productService.detail(id);
    }

    @Operation(summary = "Tạo sản phẩm")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã tạo"),
            @ApiResponse(responseCode = "404", description = "Danh mục không tồn tại",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_NAME",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COO')")
    public ProductDto create(@RequestBody @Valid SaveProductRequest request) {
        return productService.create(request);
    }

    @Operation(summary = "Cập nhật sản phẩm")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_NAME",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COO')")
    public ProductDto update(@PathVariable Integer id, @RequestBody @Valid SaveProductRequest request) {
        return productService.update(id, request);
    }

    @Operation(summary = "Xoá mềm sản phẩm", description = "Chuyển INACTIVE (đúng đặc tả).")
    @ApiResponse(responseCode = "200", description = "OK")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('COO')")
    public ProductDto softDelete(@PathVariable Integer id) {
        return productService.softDelete(id);
    }

    @Operation(summary = "Xoá cứng sản phẩm",
            description = "Xoá kèm giá size và topping; chặn khi voucher còn tham chiếu sản phẩm.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá"),
            @ApiResponse(responseCode = "409", description = "RESOURCE_CONFLICT — voucher còn tham chiếu",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('COO')")
    public void hardDelete(@PathVariable Integer id) {
        productService.hardDelete(id);
    }

    @Operation(summary = "Danh sách giá size của sản phẩm")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping("/{id}/sizes")
    @PreAuthorize("isAuthenticated()")
    public List<ProductSizeDto> sizes(@PathVariable Integer id) {
        return productSizeService.list(id);
    }

    @Operation(summary = "Thêm/cập nhật giá size", description = "Upsert theo size (S|M|L).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Sản phẩm không tồn tại",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping("/{id}/sizes")
    @PreAuthorize("hasAuthority('COO')")
    public ProductSizeDto upsertSize(@PathVariable Integer id,
                                     @RequestBody @Valid SaveProductSizeRequest request) {
        return productSizeService.upsert(id, request);
    }

    @Operation(summary = "Xoá một mức giá size", description = "Không xoá được size đang là size mặc định.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá"),
            @ApiResponse(responseCode = "422", description = "DEFAULT_SIZE_IN_USE",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @DeleteMapping("/{id}/sizes/{size}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('COO')")
    public void deleteSize(@PathVariable Integer id, @PathVariable ProductSizeName size) {
        productSizeService.delete(id, size);
    }

    @Operation(summary = "Thay toàn bộ topping của sản phẩm", description = "Gửi mảng rỗng để bỏ hết topping.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Topping trùng hoặc không tồn tại",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PutMapping("/{id}/toppings")
    @PreAuthorize("hasAuthority('COO')")
    public ProductDetailDto replaceToppings(@PathVariable Integer id,
                                            @RequestBody @Valid SaveProductToppingsRequest request) {
        return productService.replaceToppings(id, request);
    }

    @Operation(summary = "Xem công thức định lượng (BOM) và chi phí ước tính của sản phẩm",
            description = "Bỏ trống size = công thức chung; có size = dòng chung + dòng của size đó kèm tính giá vốn ước tính.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = ProductRecipeResponse.class)))
    @GetMapping("/{id}/recipe")
    @PreAuthorize("isAuthenticated()")
    public ProductRecipeResponse recipe(@PathVariable Integer id,
                                        @ModelAttribute @Valid RecipeProductQuery query) {
        return recipeService.getRecipe(id, query.size());
    }

    @Operation(summary = "Cập nhật công thức định lượng (BOM) cho sản phẩm",
            description = "Lưu hoặc thay thế định lượng nguyên vật liệu cho một size (hoặc size chung nếu size=null). Tự động tính toán COGS ước tính.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lưu thành công",
                    content = @Content(schema = @Schema(implementation = ProductRecipeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ (nguyên liệu trùng lặp, không tồn tại hoặc INACTIVE)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Sản phẩm không tồn tại",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/{id}/recipe")
    @PreAuthorize("hasAuthority('COO')")
    public ProductRecipeResponse saveRecipe(@PathVariable Integer id,
                                            @RequestBody @Valid SaveProductRecipeRequest request,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return recipeService.saveRecipe(id, request, principal);
    }

    @Operation(summary = "Xoá công thức định lượng của sản phẩm",
            description = "Xoá công thức định lượng cho một size cụ thể, hoặc toàn bộ định lượng của sản phẩm nếu không chỉ định size.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá thành công"),
            @ApiResponse(responseCode = "404", description = "Sản phẩm không tồn tại",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{id}/recipe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('COO')")
    public void deleteRecipe(@PathVariable Integer id,
                             @RequestParam(required = false) ProductSizeName size,
                             @AuthenticationPrincipal UserPrincipal principal) {
        recipeService.deleteRecipe(id, size, principal);
    }
}
