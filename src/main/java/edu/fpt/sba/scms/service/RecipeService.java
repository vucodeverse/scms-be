package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.dto.request.SaveProductRecipeRequest;
import edu.fpt.sba.scms.dto.response.ProductRecipeResponse;
import edu.fpt.sba.scms.dto.response.RecipeLineDto;
import edu.fpt.sba.scms.security.principal.UserPrincipal;

import java.util.List;

/**
 * Quản lý công thức định lượng (Bill of Materials - BOM) cho sản phẩm (UC12).
 */
public interface RecipeService {

    /**
     * Lấy công thức định lượng áp dụng cho sản phẩm và size tương ứng (UC12, §10.7).
     * Bỏ trống size = công thức chung; có size = dòng chung + dòng của size đó kèm tính giá vốn ước tính.
     */
    ProductRecipeResponse getRecipe(Integer productId, ProductSizeName size);

    /**
     * Lấy danh sách thô các dòng công thức để tương thích với API đọc hiện hữu.
     */
    List<RecipeLineDto> listRecipeLines(Integer productId, ProductSizeName size);

    /**
     * Lưu hoặc cập nhật công thức định lượng của sản phẩm cho một size (hoặc size = null).
     */
    ProductRecipeResponse saveRecipe(Integer productId, SaveProductRecipeRequest request, UserPrincipal actor);

    /**
     * Xoá định lượng của sản phẩm cho một size cụ thể hoặc toàn bộ định lượng của sản phẩm.
     */
    void deleteRecipe(Integer productId, ProductSizeName size, UserPrincipal actor);
}

