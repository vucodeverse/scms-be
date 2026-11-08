package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.SyncConfigRequest;
import edu.fpt.sba.scms.dto.response.SyncConfigResponse;
import edu.fpt.sba.scms.security.principal.UserPrincipal;

/**
 * Đồng bộ cấu hình danh mục, sản phẩm, khuyến mãi từ chuỗi xuống các chi nhánh (UC14).
 */
public interface SyncConfigService {

    SyncConfigResponse syncToBranches(SyncConfigRequest request, UserPrincipal principal);
}

