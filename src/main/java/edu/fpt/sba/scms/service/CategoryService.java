package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.OrderCategoryRequest;
import edu.fpt.sba.scms.dto.request.SaveCategoryRequest;
import edu.fpt.sba.scms.dto.request.SaveCategoryStatusRequest;
import edu.fpt.sba.scms.dto.request.query.CategoryListQuery;
import edu.fpt.sba.scms.dto.response.CategoryDetailDto;
import edu.fpt.sba.scms.dto.response.CategoryDto;
import org.springframework.data.domain.Page;

/** UC09 — Manage Menu Categories (IMPLEMENTATION.md §10.7). */
public interface CategoryService {

    /** Phân trang 10 phần tử/trang mặc định; lọc keyword/status, sắp xếp theo whitelist. */
    Page<CategoryDto> list(CategoryListQuery query);

    CategoryDetailDto detail(Integer id);

    CategoryDto create(SaveCategoryRequest request);

    CategoryDto update(Integer id, SaveCategoryRequest request);

    /** "Ngừng" danh mục: còn sản phẩm ACTIVE/AVAILABLE → 422 CATEGORY_NOT_EMPTY. */
    CategoryDto changeStatus(Integer id, SaveCategoryStatusRequest request);

    /** Gán lại sortOrder cho toàn bộ danh mục đang hoạt động theo thứ tự client gửi. */
    void reorder(OrderCategoryRequest request);

    /** Xoá mềm: chuyển INACTIVE (đúng đặc tả). Điều kiện như {@link #changeStatus}. */
    CategoryDto softDelete(Integer id);

    /**
     * Xoá cứng (bổ sung ngoài đặc tả, theo yêu cầu dự án). Chỉ xoá khi không còn dữ liệu mồ côi:
     * không còn sản phẩm trong danh mục và không voucher nào tham chiếu. Không xoá danh mục hệ thống UNCAT.
     */
    void hardDelete(Integer id);
}
