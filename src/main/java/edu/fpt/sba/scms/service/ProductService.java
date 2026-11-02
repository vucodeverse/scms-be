package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.dto.request.SaveProductRequest;
import edu.fpt.sba.scms.dto.request.SaveProductToppingsRequest;
import edu.fpt.sba.scms.dto.request.query.ProductListQuery;
import edu.fpt.sba.scms.dto.response.ProductDetailDto;
import edu.fpt.sba.scms.dto.response.ProductDto;
import edu.fpt.sba.scms.dto.response.RecipeLineDto;
import org.springframework.data.domain.Page;

import java.util.List;

/** UC10 — Manage Products (IMPLEMENTATION.md §10.7). Size thuộc {@link ProductSizeService}. */
public interface ProductService {

    Page<ProductDto> list(ProductListQuery query);

    ProductDetailDto detail(Integer id);

    ProductDto create(SaveProductRequest request);

    ProductDto update(Integer id, SaveProductRequest request);

    /** Thay toàn bộ topping của sản phẩm (danh sách rỗng = bỏ hết topping). */
    ProductDetailDto replaceToppings(Integer id, SaveProductToppingsRequest request);

    /** Xoá mềm: chuyển INACTIVE (đúng đặc tả). */
    ProductDto softDelete(Integer id);

    /** Xoá cứng (bổ sung ngoài đặc tả): kèm size, topping, công thức; chặn khi voucher còn tham chiếu. */
    void hardDelete(Integer id);

    /** UC12 (chỉ đọc): công thức của sản phẩm, lọc theo size nếu có. */
    List<RecipeLineDto> recipe(Integer id, ProductSizeName size);
}
