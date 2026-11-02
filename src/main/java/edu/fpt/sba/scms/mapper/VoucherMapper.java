package edu.fpt.sba.scms.mapper;

import edu.fpt.sba.scms.dto.response.CategoryRefDto;
import edu.fpt.sba.scms.dto.response.ProductRefDto;
import edu.fpt.sba.scms.dto.response.VoucherDetailDto;
import edu.fpt.sba.scms.dto.response.VoucherDto;
import edu.fpt.sba.scms.model.Voucher;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Entity ↔ DTO của voucher (UC13). Phạm vi áp dụng do service đọc từ bảng liên kết. */
@Mapper(componentModel = "spring")
public interface VoucherMapper {

    VoucherDto toDto(Voucher voucher);

    @Mapping(target = "products", source = "products")
    @Mapping(target = "categories", source = "categories")
    VoucherDetailDto toDetail(Voucher voucher, List<ProductRefDto> products, List<CategoryRefDto> categories);
}
