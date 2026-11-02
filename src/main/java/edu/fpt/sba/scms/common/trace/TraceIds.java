package edu.fpt.sba.scms.common.trace;

import org.slf4j.MDC;

/**
 * Truy cập traceId qua hằng số MDC thuần — cho các tầng không được phép đọc {@code config}
 * ({@code common}, {@code dto}, {@code controller}).
 * <p>
 * Nguồn sự thật của hằng số và việc ghi/đọc MDC nằm ở {@code config.TraceContextFilter}; class này
 * <b>không</b> phụ thuộc filter để tránh vòng phụ thuộc {@code common -> config}.
 */
public final class TraceIds {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    private TraceIds() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
