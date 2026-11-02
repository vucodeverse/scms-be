package edu.fpt.sba.scms.service.support;

import edu.fpt.sba.scms.common.enums.SortDirection;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.dto.PageQueryDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Xử lý chung của query danh sách master data (UC09/UC10/UC13): chuẩn hoá từ khoá, chọn thứ tự sắp xếp theo
 * whitelist, quy đổi phân trang. Đặt ở service (không ở DTO) để một nơi chịu trách nhiệm "null nghĩa là gì".
 */
public final class MasterDataQueries {

    private MasterDataQueries() {
    }

    /**
     * Chọn trường sắp xếp theo whitelist của từng danh sách.
     *
     * @throws InvalidFieldValueException khi client gửi trường ngoài whitelist (→ 400 kèm fieldErrors)
     */
    public static Sort sort(String sortBy, SortDirection direction, Map<String, Sort> allowed, Sort defaultSort) {
        Sort chosen = sortBy == null ? defaultSort : allowed.get(sortBy);
        if (chosen == null) {
            throw new InvalidFieldValueException("sortBy", "Trường sắp xếp không hợp lệ: " + sortBy
                    + ". Chấp nhận: " + String.join(", ", allowed.keySet()) + ".");
        }
        return direction == SortDirection.DESC ? chosen.descending() : chosen.ascending();
    }

    /** null page/size → mặc định 1/10 (xem {@link PageQueryDefaults}). */
    public static Pageable pageable(Integer page, Integer size, Sort sort) {
        return PageQueryDefaults.toPageable(page, size, sort);
    }
}
