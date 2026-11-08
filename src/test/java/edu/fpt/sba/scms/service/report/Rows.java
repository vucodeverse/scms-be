package edu.fpt.sba.scms.service.report;

import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Dựng projection interface của repository từ cặp key/value — để test calculator không cần DB.
 * Ví dụ: {@code Rows.of(UtcHourRow.class, "yr", 2026, "orderCount", 3L)}; key thiếu → getter trả {@code null}.
 */
final class Rows {

    private static final SpelAwareProxyProjectionFactory FACTORY = new SpelAwareProxyProjectionFactory();

    private Rows() {
    }

    static <T> T of(Class<T> type, Object... keyValues) {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            values.put((String) keyValues[i], keyValues[i + 1]);
        }
        return FACTORY.createProjection(type, values);
    }
}
