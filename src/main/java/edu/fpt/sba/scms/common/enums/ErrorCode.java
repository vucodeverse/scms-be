package edu.fpt.sba.scms.common.enums;

import org.springframework.http.HttpStatus;

/**
 * Mã lỗi 1-1 với cây exception (IMPLEMENTATION.md §8.3).
 * {@code code} trả FE; {@code status} là HTTP status mặc định.
 * Mã con 422 nằm ở {@link BusinessErrorCode}.
 */
public enum ErrorCode {

    // ---- 400 ----
    INVALID_INPUT_PAYLOAD("Dữ liệu gửi lên không hợp lệ.", HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR("Dữ liệu không hợp lệ.", HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED("Phương thức HTTP không được hỗ trợ cho đường dẫn này.", HttpStatus.METHOD_NOT_ALLOWED),
    NOT_ACCEPTABLE("Định dạng phản hồi không được hỗ trợ.", HttpStatus.NOT_ACCEPTABLE),
    UNSUPPORTED_MEDIA_TYPE("Kiểu dữ liệu gửi lên không được hỗ trợ.", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // ---- 401 ----
    AUTH_REQUIRED("Vui lòng đăng nhập.", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_EXPIRED("Phiên đăng nhập đã hết hạn.", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_INVALID("Token không hợp lệ.", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("Tên đăng nhập hoặc mật khẩu không đúng.", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("Tài khoản tạm khóa do đăng nhập sai quá nhiều lần.", HttpStatus.UNAUTHORIZED),
    ACCOUNT_INACTIVE("Tài khoản đã bị vô hiệu hoá.", HttpStatus.UNAUTHORIZED),
    INVALID_PIN("Mã PIN không đúng.", HttpStatus.UNAUTHORIZED),
    INVALID_KIOSK_KEY("Kiosk key không hợp lệ.", HttpStatus.UNAUTHORIZED),

    // ---- 403 ----
    ACCESS_DENIED("Bạn không có quyền thực hiện thao tác này.", HttpStatus.FORBIDDEN),
    CROSS_BRANCH_DENIED("Không được truy cập dữ liệu chi nhánh khác.", HttpStatus.FORBIDDEN),

    // ---- 404 ----
    RESOURCE_NOT_FOUND("Không tìm thấy dữ liệu.", HttpStatus.NOT_FOUND),

    // ---- 409 ----
    RESOURCE_CONFLICT("Dữ liệu đã tồn tại.", HttpStatus.CONFLICT),
    DUPLICATE_CODE("Mã đã tồn tại.", HttpStatus.CONFLICT),
    DUPLICATE_NAME("Tên đã tồn tại.", HttpStatus.CONFLICT),
    DUPLICATE_USERNAME("Tên đăng nhập đã tồn tại.", HttpStatus.CONFLICT),
    DUPLICATE_EMAIL("Email đã tồn tại.", HttpStatus.CONFLICT),
    DUPLICATE_PIN("PIN đã được dùng trong chi nhánh.", HttpStatus.CONFLICT),
    DUPLICATE_CARD("Thẻ đã gắn với nhân viên khác.", HttpStatus.CONFLICT),
    SCHEDULE_CONFLICT("Lịch làm việc bị trùng.", HttpStatus.CONFLICT),
    INVALID_STATE_TRANSITION("Dữ liệu đã thay đổi hoặc trạng thái không hợp lệ, vui lòng tải lại.", HttpStatus.CONFLICT),

    // ---- 422 ----
    BUSINESS_RULE_VIOLATION("Vi phạm quy tắc nghiệp vụ.", HttpStatus.UNPROCESSABLE_CONTENT),

    // ---- 429 ----
    TOO_MANY_REQUESTS("Bạn thao tác quá nhanh, vui lòng thử lại sau.", HttpStatus.TOO_MANY_REQUESTS),

    // ---- 503 ----
    INTEGRATION_UNAVAILABLE("Dịch vụ tích hợp tạm thời không khả dụng.", HttpStatus.SERVICE_UNAVAILABLE),

    // ---- 500 ----
    INTERNAL_SERVER_ERROR("Hệ thống gặp sự cố, vui lòng thử lại sau.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus status;

    ErrorCode(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }

    public String message() {
        return message;
    }

    public HttpStatus status() {
        return status;
    }
}
