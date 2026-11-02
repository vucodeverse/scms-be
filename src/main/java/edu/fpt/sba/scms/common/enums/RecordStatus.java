package edu.fpt.sba.scms.common.enums;

/**
 * Trạng thái bật/tắt chung của master data (category, topping, voucher — IMPLEMENTATION.md §7.1).
 * Dùng chung một enum thay vì mỗi bảng một enum để FE chỉ cần hiểu một tập giá trị.
 */
public enum RecordStatus {
    ACTIVE,
    INACTIVE
}
