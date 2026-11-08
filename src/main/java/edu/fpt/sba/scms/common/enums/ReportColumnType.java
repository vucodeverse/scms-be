package edu.fpt.sba.scms.common.enums;

/**
 * Kiểu dữ liệu một cột trong file báo cáo xuất ra — quyết định định dạng ô (UC36).
 */
public enum ReportColumnType {
    /** Chuỗi, ngày, giờ (đã định dạng sẵn). */
    TEXT,
    /** Số nguyên (số đơn, số ca...). */
    INTEGER,
    /** Số lượng có phần thập phân (tồn kho, giờ công). */
    DECIMAL,
    /** Tiền VND. */
    MONEY,
    /** Phần trăm (giá trị 12.5 = 12.5%). */
    PERCENT
}
