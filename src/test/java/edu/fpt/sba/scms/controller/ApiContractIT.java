package edu.fpt.sba.scms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hợp đồng response với FE: một khoá {@code code} cho cả thành công lẫn lỗi, timestamp ISO-8601, traceId luôn có,
 * lỗi 4xx do Spring MVC tự ném mang mã có thật trong {@code ErrorCode}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiContractIT {

    /** Instant mặc định của Jackson 3 là ISO-8601 UTC — không phải epoch millis (đây là hợp đồng với FE). */
    private static final String ISO_INSTANT = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void errorEnvelopeHasCodeIsoTimestampAndTraceIdButNoErrorCodeKey() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.timestamp", matchesPattern(ISO_INSTANT)))
                .andExpect(jsonPath("$.traceId", not(emptyOrNullString())))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    void successEnvelopeHasCodeAndIsoTimestamp() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"coo","password":"Admin@123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.timestamp", matchesPattern(ISO_INSTANT)))
                .andExpect(jsonPath("$.traceId", not(emptyOrNullString())));
    }

    /**
     * 405 do Spring MVC tự ném trước đây trả {@code code = "METHOD_NOT_ALLOWED"} từ tên status — mã không có trong
     * enum nên lệch hợp đồng với các lỗi khác. Giờ phải là mã thật và vẫn giữ HTTP status.
     */
    @Test
    void framework4xxKeepsStatusAndUsesRealErrorCode() throws Exception {
        mockMvc.perform(put("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.timestamp", matchesPattern(ISO_INSTANT)))
                .andExpect(jsonPath("$.traceId", not(emptyOrNullString())));
    }
}
