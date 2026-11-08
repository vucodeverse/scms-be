package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.PingTestRequest;
import edu.fpt.sba.scms.dto.request.UpdatePaymentConfigRequest;
import edu.fpt.sba.scms.dto.response.PaymentConfigResponse;
import edu.fpt.sba.scms.dto.response.PingTestResponse;
import edu.fpt.sba.scms.model.PaymentConfig;
import edu.fpt.sba.scms.security.principal.UserPrincipal;

public interface PaymentConfigService {

    PaymentConfigResponse getConfig();

    PaymentConfigResponse updateConfig(UpdatePaymentConfigRequest request, UserPrincipal principal);

    PingTestResponse pingTest(PingTestRequest request);

    PaymentConfig getInternalConfig();
}

