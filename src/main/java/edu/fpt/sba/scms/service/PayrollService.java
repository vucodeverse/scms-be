package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.CreatePayPeriodRequest;
import edu.fpt.sba.scms.dto.request.UpdatePayrollItemRequest;
import edu.fpt.sba.scms.dto.response.PayPeriodResponse;
import edu.fpt.sba.scms.dto.response.PayrollItemResponse;
import edu.fpt.sba.scms.security.principal.UserPrincipal;

import java.util.List;

/**
 * Quản lý tính lương chi nhánh (UC45, BR-53).
 */
public interface PayrollService {

    PayPeriodResponse createPayPeriod(CreatePayPeriodRequest request, UserPrincipal principal);

    PayrollItemResponse updatePayrollItem(Integer itemId, UpdatePayrollItemRequest request, UserPrincipal principal);

    PayPeriodResponse submitPayroll(Integer periodId, UserPrincipal principal);

    PayPeriodResponse approvePayroll(Integer periodId, UserPrincipal principal);

    PayPeriodResponse rejectPayroll(Integer periodId, String reason, UserPrincipal principal);

    List<PayPeriodResponse> listPayPeriods(Integer branchId);

    List<PayrollItemResponse> getPayrollItems(Integer periodId);
}

