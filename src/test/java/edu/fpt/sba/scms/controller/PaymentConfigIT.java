package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.repository.PaymentConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentConfigIT {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentConfigRepository paymentConfigRepository;

    @BeforeEach
    void setUp() {
        paymentConfigRepository.deleteAll();
    }

    private String cooToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"username\":\"coo\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken")
                .asText();
    }

    @Test
    void testGetConfig_Success_MaskedToken() throws Exception {
        String token = cooToken();

        mockMvc.perform(get("/api/v1/settings/sepay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.bankName").isNotEmpty())
                .andExpect(jsonPath("$.data.accountNumber").isNotEmpty())
                .andExpect(jsonPath("$.data.paymentPrefix").value("CAFE"))
                .andExpect(jsonPath("$.data.maskedApiToken").value(org.hamcrest.Matchers.containsString("******")));
    }

    @Test
    void testUpdateConfig_Success() throws Exception {
        String token = cooToken();

        String updatePayload = """
                {
                    "bankName": "Techcombank",
                    "accountNumber": "19033333338888",
                    "accountHolder": "CONG TY TNHH CAFE VIET",
                    "paymentPrefix": "CF",
                    "apiToken": "new_sepay_token_99998888",
                    "webhookUrl": "https://api.scms.vn/api/v1/webhooks/sepay",
                    "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/settings/sepay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankName").value("Techcombank"))
                .andExpect(jsonPath("$.data.accountNumber").value("19033333338888"))
                .andExpect(jsonPath("$.data.paymentPrefix").value("CF"))
                .andExpect(jsonPath("$.data.maskedApiToken").value("new_******8888"));

        // Verify config updated in GET
        mockMvc.perform(get("/api/v1/settings/sepay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankName").value("Techcombank"))
                .andExpect(jsonPath("$.data.paymentPrefix").value("CF"));
    }

    @Test
    void testPingTest_Success() throws Exception {
        String token = cooToken();

        String pingPayload = """
                {
                    "apiToken": "test_token_12345",
                    "webhookUrl": "https://api.scms.vn/api/v1/webhooks/sepay"
                }
                """;

        mockMvc.perform(post("/api/v1/settings/sepay/ping")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(pingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONNECTED"));
    }

    @Test
    void testPingTest_InvalidUrl_ThrowsBusinessRuleException() throws Exception {
        String token = cooToken();

        String invalidPayload = """
                {
                    "apiToken": "test_token_12345",
                    "webhookUrl": "ftp://invalid-url"
                }
                """;

        mockMvc.perform(post("/api/v1/settings/sepay/ping")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(invalidPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.detailCode").value("REASON_REQUIRED"));
    }

    @Test
    void testGetConfig_WithoutAuth_Fails() throws Exception {
        mockMvc.perform(get("/api/v1/settings/sepay"))
                .andExpect(status().isUnauthorized());
    }
}
