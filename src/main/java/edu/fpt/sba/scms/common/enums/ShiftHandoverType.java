package edu.fpt.sba.scms.common.enums;

/**
 * Phân loại biên bản bàn giao két tiền trong ca.
 */
public enum ShiftHandoverType {
    /** Bàn giao két giữa ca cho Thu ngân / người trực tiếp theo mà vẫn giữ ca MỞ. */
    MID_SHIFT,
    /** Thu ngân bàn giao két cuối ca chờ Quản lý chi nhánh xác nhận nhận tiền & đóng ca (kèm tùy chọn rút cất két). */
    MANAGER_CLOSE
}
