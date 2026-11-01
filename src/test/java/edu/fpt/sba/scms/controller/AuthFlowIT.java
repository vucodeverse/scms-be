package edu.fpt.sba.scms.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC01 — luồng login -> me -> refresh -> logout trên H2, qua toàn bộ filter chain + envelope.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String login(String username, String password, boolean rememberMe) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","rememberMe":%s}
                                """.formatted(username, password, rememberMe)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    void loginReturnsEnvelopeWithTokenAndLanding() throws Exception {
        String body = login("coo", "Admin@123", true);
        JsonNode json = objectMapper.readTree(body);

        assertThat(json.at("/data/accessToken").asText()).isNotBlank();
        assertThat(json.at("/data/refreshToken").asText()).isNotBlank();
        assertThat(json.at("/data/expiresInSec").asLong()).isEqualTo(1800);
        assertThat(json.at("/data/landing").asText()).isEqualTo("SCR-10");
        assertThat(json.at("/data/user/role").asText()).isEqualTo("COO");
        assertThat(json.at("/data/user/fullName").asText()).isEqualTo("COO Demo");
        assertThat(json.at("/traceId").asText()).isNotBlank();
    }

    @Test
    void loginWithoutRememberMeHasNoRefreshToken() throws Exception {
        JsonNode json = objectMapper.readTree(login("coo", "Admin@123", false));
        JsonNode refreshToken = json.at("/data/refreshToken");
        assertThat(refreshToken.isMissingNode() || refreshToken.isNull()).isTrue();
    }

    @Test
    void wrongPasswordReturns401InvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"coo","password":"sai-mat-khau"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    /**
     * Username không tồn tại vẫn phải chạy BCrypt với hash giả rồi trả 401 — không được 500.
     * (Regression: hash giả sai định dạng khiến {@code matches()} ném IllegalArgumentException, vừa lỗi 500
     * vừa làm lộ username qua khác biệt thời gian phản hồi.)
     */
    @Test
    void unknownUsernameReturns401InvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"khong-ton-tai","password":"bat-ky-mat-khau"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void blankUsernameReturns400ValidationErrorWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItems("username", "password")));
    }

    @Test
    void meRequiresTokenAndWorksWithToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));

        String accessToken = objectMapper.readTree(login("coo", "Admin@123", false)).at("/data/accessToken").asText();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("coo"))
                .andExpect(jsonPath("$.data.role").value("COO"))
                .andExpect(jsonPath("$.data.permissions").isArray());
    }

    @Test
    void refreshRotatesTokenAndRejectsOldOne() throws Exception {
        JsonNode loginJson = objectMapper.readTree(login("coo", "Admin@123", true));
        String refreshToken = loginJson.at("/data/refreshToken").asText();

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String newRefresh = objectMapper.readTree(refreshed.getResponse().getContentAsString())
                .at("/data/refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(refreshToken);

        // Token cũ đã bị thu hồi -> 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));
    }

    @Test
    void logoutRevokesAccessAndRefresh() throws Exception {
        JsonNode loginJson = objectMapper.readTree(login("coo", "Admin@123", true));
        String access = loginJson.at("/data/accessToken").asText();
        String refresh = loginJson.at("/data/refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Access token đã logout bị chặn
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        // Refresh token đã bị thu hồi
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openApiAndSwaggerAreExposed() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("SCMS API"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void publicHealthProbeIsAllowed() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
