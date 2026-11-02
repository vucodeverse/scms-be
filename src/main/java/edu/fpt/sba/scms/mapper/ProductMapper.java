package edu.fpt.sba.scms.mapper;

import edu.fpt.sba.scms.dto.response.ProductDetailDto;
import edu.fpt.sba.scms.dto.response.ProductDto;
import edu.fpt.sba.scms.dto.response.ProductSizeDto;
import edu.fpt.sba.scms.dto.response.ProductToppingDto;
import edu.fpt.sba.scms.dto.response.RecipeLineDto;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.ProductSize;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Entity ↔ DTO của sản phẩm (UC10/UC11). Tên danh mục, topping, công thức do service gom sẵn (tránh N+1). */
@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "categoryName", source = "categoryName")
    @Mapping(target = "categoryCode", source = "categoryCode")
    @Mapping(target = "sizes", source = "sizes")
    @Mapping(target = "toppings", source = "toppings")
    ProductDto toDto(Product product, String categoryName, String categoryCode,
                     List<ProductSizeDto> sizes, List<ProductToppingDto> toppings);

    @Mapping(target = "categoryName", source = "categoryName")
    @Mapping(target = "categoryCode", source = "categoryCode")
    @Mapping(target = "sizes", source = "sizes")
    @Mapping(target = "toppings", source = "toppings")
    @Mapping(target = "recipe", source = "recipe")
    ProductDetailDto toDetail(Product product, String categoryName, String categoryCode,
                              List<ProductSizeDto> sizes, List<ProductToppingDto> toppings,
                              List<RecipeLineDto> recipe);

    /** Các field còn lại (id, size, price) trùng tên. */
    ProductSizeDto toDto(ProductSize size);
}
