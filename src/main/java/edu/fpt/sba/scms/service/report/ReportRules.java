package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.StocktakeStatus;

import java.util.Set;

/**
 * Các tập trạng thái quyết định "dữ liệu nào được tính" — một nguồn duy nhất cho mọi report.
 */
public final class ReportRules {

    /**
     * Đơn được ghi nhận doanh thu (BR-26/BR-46..50): đã thanh toán, kể cả đơn hoàn một phần (phần còn lại vẫn là
     * doanh thu). Loại {@code PENDING}, {@code CANCELLED} và đơn đã hoàn toàn bộ ({@code REFUNDED}).
     */
    public static final Set<OrderStatus> REVENUE_ORDER_STATUSES =
            Set.of(OrderStatus.PAID, OrderStatus.PARTIALLY_REFUNDED);

    /** Ca đã mở két (ca chỉ mới lên lịch chưa có dòng tiền — UC37). */
    public static final Set<ShiftStatus> CASH_SHIFT_STATUSES = Set.of(ShiftStatus.OPEN, ShiftStatus.CLOSED);

    /** Phiên kiểm kê đã cân đối — số đếm của các phiên này là "lần kiểm kê" hợp lệ (UC38, BR-44). */
    public static final Set<StocktakeStatus> COUNTED_STOCKTAKE_STATUSES =
            Set.of(StocktakeStatus.ADJUSTED, StocktakeStatus.CLOSED);

    /** Sản phẩm còn trong thực đơn — ứng viên của danh sách bán chậm (UC41). */
    public static final Set<ProductStatus> MENU_PRODUCT_STATUSES =
            Set.of(ProductStatus.AVAILABLE, ProductStatus.UNAVAILABLE);

    private ReportRules() {
    }
}
