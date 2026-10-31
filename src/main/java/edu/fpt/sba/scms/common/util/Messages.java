package edu.fpt.sba.scms.common.util;

/**
 * Thông điệp chuẩn SRS §5.3 (IMPLEMENTATION.md §8.10). MSG05/MSG06 bỏ (không thuộc hệ thống).
 */
public final class Messages {

    public static final String MSG01 = "Không tìm thấy kết quả.";
    public static final String MSG02_FORMAT = "Trường %s là bắt buộc.";
    public static final String MSG03 = "Cập nhật thành công.";
    public static final String MSG04 = "Thêm mới thành công.";
    public static final String MSG07 = "Xoá thành công.";
    public static final String MSG08_FORMAT = "Vượt quá độ dài tối đa %d ký tự.";
    public static final String MSG09 = "Tên đăng nhập hoặc mật khẩu không đúng.";

    private Messages() {
    }

    public static String required(String field) {
        return MSG02_FORMAT.formatted(field);
    }
}
