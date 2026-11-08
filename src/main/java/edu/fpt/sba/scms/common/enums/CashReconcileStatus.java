package edu.fpt.sba.scms.common.enums;

/**
 * Trạng thái đối soát tiền mặt của một ca trong báo cáo dòng tiền (UC37).
 */
public enum CashReconcileStatus {
    /** Ca đã đóng và đã đếm tiền: có số chênh lệch. */
    RECONCILED,
    /** Ca chưa đóng / chưa đếm tiền: không tính chênh lệch (UC37 AT2). */
    UNRECONCILED
}
