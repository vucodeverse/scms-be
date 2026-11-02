package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.dto.request.SaveProductSizeRequest;
import edu.fpt.sba.scms.dto.response.ProductSizeDto;

import java.util.List;

/** UC11 — giá theo size của sản phẩm (IMPLEMENTATION.md §10.7). */
public interface ProductSizeService {

    List<ProductSizeDto> list(Integer productId);

    /** Upsert: đã có size thì cập nhật giá, chưa có thì tạo. */
    ProductSizeDto upsert(Integer productId, SaveProductSizeRequest request);

    /**
     * Xoá cứng một dòng giá (bổ sung ngoài đặc tả — bảng {@code product_size} không có cột trạng thái riêng,
     * nên đây là cách duy nhất bỏ một size khỏi sản phẩm). Chặn khi size đang là size mặc định của sản phẩm.
     */
    void delete(Integer productId, ProductSizeName size);
}
