package edu.fpt.sba.scms.common.enums;

/**
 * Kết quả so sánh tồn sổ với lần kiểm kê gần nhất của một nguyên liệu (UC38).
 */
public enum StockCountStatus {
    MATCHED,
    VARIANCE,
    /** Chưa từng được kiểm kê (UC38 AT2). */
    UNCOUNTED
}
