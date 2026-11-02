package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.SaveToppingRequest;
import edu.fpt.sba.scms.dto.response.ToppingDto;

import java.util.List;

/** UC11 — danh mục topping (IMPLEMENTATION.md §10.7). Không phân trang: số topping nhỏ và FE chọn trong dropdown. */
public interface ToppingService {

    List<ToppingDto> list();

    ToppingDto create(SaveToppingRequest request);

    ToppingDto update(Integer id, SaveToppingRequest request);

    /** Xoá mềm: chuyển INACTIVE (đúng đặc tả). */
    ToppingDto softDelete(Integer id);

    /** Xoá cứng (bổ sung ngoài đặc tả): gỡ topping khỏi mọi sản phẩm rồi xoá dòng. */
    void hardDelete(Integer id);
}
