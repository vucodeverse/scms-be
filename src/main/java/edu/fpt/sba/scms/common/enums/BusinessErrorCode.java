package edu.fpt.sba.scms.common.enums;

/**
 * Mã con của 422 BUSINESS_RULE_VIOLATION (IMPLEMENTATION.md §8.3).
 * Mỗi mã đi kèm thông điệp tiếng Việt mặc định; service được phép truyền thông điệp chi tiết hơn.
 */
public enum BusinessErrorCode {

    VOUCHER_EXPIRED("Voucher đã hết hạn."),
    VOUCHER_MIN_NOT_MET("Chưa đạt giá trị tối thiểu để áp voucher."),
    VOUCHER_USAGE_EXCEEDED("Voucher đã hết lượt sử dụng."),
    VOUCHER_ALREADY_APPLIED("Đơn hàng đã áp một voucher."),
    CATEGORY_NOT_EMPTY("Danh mục còn sản phẩm, không thể xoá."),
    INSUFFICIENT_CASH("Số dư ngăn kéo không đủ."),
    CASH_RECEIVED_LT_TOTAL("Tiền khách đưa nhỏ hơn tổng tiền."),
    ORDER_FROZEN("Đơn hàng đã thanh toán, không thể thay đổi."),
    SHIFT_NOT_OPEN("Chi nhánh chưa mở ca."),
    SHIFT_ALREADY_OPEN("Chi nhánh đang có ca mở."),
    ATTENDANCE_MIN_INTERVAL("Khoảng cách chấm công tối thiểu 15 phút."),
    ATTENDANCE_NO_OPEN("Chưa có lượt check-in để check-out."),
    ATTENDANCE_ALREADY_CHECKED_IN("Bạn đã check-in, vui lòng check-out trước."),
    INSUFFICIENT_STOCK("Tồn kho không đủ."),
    PACK_UNIT_REQUIRED("Phải nhập theo đơn vị nguyên kiện."),
    REASON_REQUIRED("Bắt buộc nhập lý do."),
    REFUND_EXCEEDS_PAID("Số tiền hoàn vượt quá số đã thanh toán."),
    PRODUCT_UNAVAILABLE("Sản phẩm hiện không khả dụng."),
    AMOUNT_MISMATCH("Số tiền chuyển khoản không khớp."),
    SELF_APPROVAL_FORBIDDEN("Không được tự phê duyệt yêu cầu của chính mình."),
    BRANCH_NOT_EMPTY("Chi nhánh còn nhân sự đang hoạt động, không thể ngừng hoạt động."),
    SELF_ACTION_FORBIDDEN("Không được tự thực hiện thao tác này trên tài khoản của chính mình."),
    VOUCHER_IN_USE("Voucher đã được sử dụng, không thể sửa hoặc xoá."),
    DEFAULT_SIZE_IN_USE("Size đang là size mặc định của sản phẩm."),
    EMPTY_INVENTORY("Chi nhánh chưa có dữ liệu tồn kho để thực hiện kiểm kê."),
    SCHEDULE_CONFLICT("Nhân viên bị trùng lịch ca làm việc."),
    INVALID_MANAGER_PIN("Mã PIN của Quản lý chi nhánh không chính xác."),
    MANAGER_PIN_REQUIRED("Thao tác yêu cầu mã PIN phê duyệt của Quản lý chi nhánh."),
    SHIFT_CLOSED("Ca làm việc đã đóng."),
    INVALID_SHIFT_TIME("Thời gian ca làm việc không hợp lệ."),
    REFUND_NOT_ALLOWED("Đơn hàng không đủ điều kiện hoàn tiền."),
    PAYROLL_LOCKED("Bảng lương đã chốt/duyệt, không thể chỉnh sửa."),
    REPORT_NO_DATA_TO_EXPORT("Không có dữ liệu để xuất."),
    SHIFT_ASSIGNMENT_REQUIRED("Nhân viên chưa được phân công ca làm việc phù hợp để chấm công."),
    SHIFT_HANDOVER_PENDING("Két tiền đang tạm khóa chờ xác nhận bàn giao giữa ca. Vui lòng hoàn tất kiểm két nhận ca trước khi giao dịch.");


    private final String message;

    BusinessErrorCode(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
