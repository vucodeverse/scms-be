package edu.fpt.sba.scms.mapper;

import edu.fpt.sba.scms.dto.response.CategoryDetailDto;
import edu.fpt.sba.scms.dto.response.CategoryDto;
import edu.fpt.sba.scms.dto.response.ProductDto;
import edu.fpt.sba.scms.model.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Entity ↔ DTO của danh mục (UC09). MapStruct sinh implementation lúc build.
 * {@code productCount}/{@code products} là dữ liệu service truy vấn sẵn (tránh N+1), không nằm trên entity.
 */
@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(target = "productCount", source = "productCount")
    CategoryDto toDto(Category category, long productCount);

    /** {@code systemCategory} đọc từ {@code Category.isSystemCategory()}. */
    @Mapping(target = "products", source = "products")
    CategoryDetailDto toDetail(Category category, List<ProductDto> products);
}
