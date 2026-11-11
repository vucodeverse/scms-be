package edu.fpt.sba.scms.common.enums;

/**
 * Trạng thái phiên kiểm kê kho (M09, SDD §5.1.13, FEA-05).
 */
public enum StocktakeStatus {
    OPEN,
    ADJUSTED,
    CLOSED,
    CANCELLED,
    DISCREPANCY_ALERT
}
