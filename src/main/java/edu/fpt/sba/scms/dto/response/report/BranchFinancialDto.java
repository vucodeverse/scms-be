package edu.fpt.sba.scms.dto.response.report;

/**
 * Số liệu tài chính của một chi nhánh (UC40). Chi nhánh không có đơn vẫn có dòng với số 0 (AT1).
 */
public record BranchFinancialDto(
        Integer branchId,
        String branchCode,
        String branchName,
        FinancialFiguresDto figures,
        boolean costIncomplete) {
}
