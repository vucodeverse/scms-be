package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.response.report.DashboardDto;

/**
 * Dashboard tổng quan trong ngày của chi nhánh (UC34).
 */
public interface DashboardService {

    /**
     * @param branchId BM: bỏ trống hoặc chi nhánh của mình; COO: bắt buộc chọn chi nhánh
     */
    DashboardDto getBranchDashboard(Integer branchId);
}
