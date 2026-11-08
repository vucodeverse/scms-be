package edu.fpt.sba.scms.common.enums;

/**
 * Các report xuất được file (UC36). {@code slug} dùng cho tên file và audit log.
 */
public enum ReportType {
    SALES("sales", "Báo cáo doanh số chi nhánh"),
    CASH_FLOW("cash-flow", "Báo cáo dòng tiền chi nhánh"),
    INVENTORY("inventory", "Báo cáo xuất - nhập - tồn kho"),
    HR_SHIFTS("hr-shifts", "Báo cáo nhân sự & ca làm việc"),
    CONSOLIDATED_FINANCIAL("consolidated-financial", "Báo cáo tài chính hợp nhất"),
    PRODUCT_SALES("product-sales", "Báo cáo sản phẩm bán chạy / bán chậm"),
    PEAK_HOURS("peak-hours", "Báo cáo giờ cao điểm");

    private final String slug;
    private final String title;

    ReportType(String slug, String title) {
        this.slug = slug;
        this.title = title;
    }

    public String slug() {
        return slug;
    }

    public String title() {
        return title;
    }
}
