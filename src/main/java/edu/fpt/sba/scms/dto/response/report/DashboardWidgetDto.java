package edu.fpt.sba.scms.dto.response.report;

import edu.fpt.sba.scms.common.enums.DashboardWidgetStatus;

/**
 * Một widget dashboard (UC34). Widget lỗi có {@code status = ERROR}, {@code data = null} và thông báo để FE hiển thị
 * empty state + nút Retry đúng widget đó (AT1).
 */
public record DashboardWidgetDto<T>(DashboardWidgetStatus status, T data, String message) {

    public static <T> DashboardWidgetDto<T> ok(T data) {
        return new DashboardWidgetDto<>(DashboardWidgetStatus.OK, data, null);
    }

    public static <T> DashboardWidgetDto<T> error(String message) {
        return new DashboardWidgetDto<>(DashboardWidgetStatus.ERROR, null, message);
    }
}
