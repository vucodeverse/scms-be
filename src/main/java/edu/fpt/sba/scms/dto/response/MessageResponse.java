package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response mang một câu thông báo cho các endpoint "không có dữ liệu trả về nhưng FE cần xác nhận đã xử lý"
 * (logout, đổi mật khẩu...). Không dùng {@code Void}/null vì Spring MVC bỏ qua ghi body khi giá trị trả về null,
 * nên envelope {@code ApiResponse} sẽ không xuất hiện.
 */
@Schema(name = "MessageResponse")
public record MessageResponse(
        @Schema(example = "Đã đăng xuất.") String message) {
}
