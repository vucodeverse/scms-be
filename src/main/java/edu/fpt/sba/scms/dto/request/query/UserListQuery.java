package edu.fpt.sba.scms.dto.request.query;

import edu.fpt.sba.scms.common.enums.SortDirection;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.dto.PageQueryDefaults;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * {@code GET /api/v1/users} (IMPLEMENTATION.md §10.2.1). Dùng chung SCR-09 và dropdown quản lý chi nhánh.
 *
 * @param role       {@code role.code} (COO | BRANCH_MANAGER | CASHIER | BARISTA | WAITER)
 * @param branchId   chỉ COO dùng; BM bị service ghi đè bằng branch trong JWT
 * @param unassigned chỉ COO dùng; true = chưa gắn chi nhánh (null branchId), false = đã gắn chi nhánh
 * @param sortBy     fullName | username | createdAt (mặc định fullName)
 */
@Schema(name = "UserListQuery")
public record UserListQuery(
        @Size(max = 100, message = "Từ khoá tối đa 100 ký tự.")
        @Schema(description = "Tìm theo họ tên, username, số điện thoại hoặc email")
        String keyword,

        @Schema(description = "Mã role", example = "BRANCH_MANAGER")
        String role,

        @Schema(description = "Chi nhánh (chỉ COO; BM luôn bị giới hạn vào chi nhánh của mình)")
        Integer branchId,

        @Schema(description = "Trạng thái tài khoản")
        UserStatus status,

        @Schema(description = "Lọc theo tình trạng gắn chi nhánh (chỉ COO): true = chưa gắn, false = đã gắn")
        Boolean unassigned,

        @Min(value = 1, message = "Trang phải bắt đầu từ 1.")
        @Schema(defaultValue = PageQueryDefaults.DEFAULT_PAGE, minimum = "1")
        Integer page,

        @Min(value = 1, message = "Số phần tử mỗi trang phải >= 1.")
        @Max(value = PageQueryDefaults.MAX_SIZE, message = "Số phần tử mỗi trang tối đa 100.")
        @Schema(defaultValue = PageQueryDefaults.DEFAULT_SIZE, maximum = "100")
        Integer size,

        @Schema(defaultValue = "ASC")
        SortDirection sortDir,

        @Schema(description = "Trường sắp xếp", defaultValue = "fullName")
        String sortBy) {
}
