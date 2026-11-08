package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.SyncConfigRequest;
import edu.fpt.sba.scms.dto.response.ProductDto;
import edu.fpt.sba.scms.dto.response.SyncConfigResponse;
import edu.fpt.sba.scms.dto.response.SyncPreviewDto;
import edu.fpt.sba.scms.security.principal.UserPrincipal;

import java.util.List;

/**
 * Đồng bộ cấu hình danh mục, sản phẩm, khuyến mãi từ chuỗi xuống các chi nhánh (UC14).
 */
public interface SyncConfigService {

    /** Lấy toàn bộ dữ liệu cấu hình chuỗi sẵn sàng đồng bộ (chi nhánh, danh mục, món ăn kèm size & voucher). */
    SyncPreviewDto getSyncPreview();

    /** Lấy danh sách món ăn kèm đầy đủ bảng giá size phục vụ chọn món đồng bộ. */
    List<ProductDto> getSyncableProducts();

    /** Thực hiện phát lệnh đồng bộ các món, danh mục, voucher đã chọn xuống các chi nhánh mục tiêu. */
    SyncConfigResponse syncToBranches(SyncConfigRequest request, UserPrincipal principal);
}
