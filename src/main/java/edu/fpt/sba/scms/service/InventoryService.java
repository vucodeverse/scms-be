package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.PageResponse;
import edu.fpt.sba.scms.dto.request.query.InventoryQuery;
import edu.fpt.sba.scms.dto.response.IngredientStockDto;
import edu.fpt.sba.scms.dto.response.LowStockAlertDto;

import java.util.List;

/**
 * Service quản lý tồn kho và cảnh báo thiếu hàng (UC33, FEA-03: POS BOM Deduction).
 */
public interface InventoryService {

    /**
     * Tra cứu sổ kho chi nhánh có phân trang, tìm kiếm và lọc trạng thái tồn.
     */
    PageResponse<IngredientStockDto> getBranchInventory(InventoryQuery query);

    /**
     * Lấy danh sách nguyên vật liệu chạm ngưỡng an toàn cần cảnh báo và gợi ý nhập bù.
     */
    List<LowStockAlertDto> getLowStockAlerts(Integer explicitBranchId);

    default List<LowStockAlertDto> getLowStockAlerts(Long explicitBranchId) {
        return getLowStockAlerts(explicitBranchId != null ? explicitBranchId.intValue() : null);
    }

    /**
     * FEA-03: Khấu trừ tồn kho Quầy Bar tự động theo công thức pha chế (BOM) khi đơn hàng POS được thanh toán.
     *
     * @param orderId Mã đơn hàng vừa thanh toán
     */
    void deductStockForOrder(Long orderId);

    default void deductStockForOrder(Integer orderId) {
        deductStockForOrder(orderId != null ? Long.valueOf(orderId) : null);
    }
}
