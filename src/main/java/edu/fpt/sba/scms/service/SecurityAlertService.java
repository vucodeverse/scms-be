package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.response.SecurityAlertDto;
import edu.fpt.sba.scms.dto.response.SecurityAlertSummaryDto;
import edu.fpt.sba.scms.security.principal.UserPrincipal;

public interface SecurityAlertService {

    SecurityAlertSummaryDto listAlerts(Integer requestedBranchId, boolean activeOnly, int limit);

    SecurityAlertDto acknowledge(Integer alertId, String note, UserPrincipal actor);

    SecurityAlertDto unlockPin(Integer alertId, String note, UserPrincipal actor);
}
