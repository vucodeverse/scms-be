package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.service.support.PinHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC01 — luồng login -> me -> refresh -> logout -> pin-login trên H2, qua toàn bộ filter chain + envelope.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIT {

    private static final AtomicInteger SEQ = new AtomicInteger(900);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PinHasher pinHasher;

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
    void cashierPinLoginListsOnlyActiveCashiersIssuesTokenAndEnforcesLockoutAndRoleGuard() throws Exception {
        int seq = SEQ.incrementAndGet();
        Branch branch = branchRepository.save(Branch.builder()
                .code("PIN_" + seq)
                .name("Chi nhánh PIN Auth " + seq)
                .openingTime(LocalTime.of(7, 0))
                .closingTime(LocalTime.of(22, 0))
                .status(BranchStatus.OPEN)
                .build());
        Branch otherBranch = branchRepository.save(Branch.builder()
                .code("PINO_" + seq)
                .name("Chi nhánh PIN Khác " + seq)
                .openingTime(LocalTime.of(7, 0))
                .closingTime(LocalTime.of(22, 0))
                .status(BranchStatus.OPEN)
                .build());

        Role cashierRole = roleRepository.findByCode(Roles.CASHIER.code())
                .orElseGet(() -> roleRepository.save(Role.builder().code(Roles.CASHIER.code()).name("Thu ngân").build()));
        Role baristaRole = roleRepository.findByCode(Roles.BARISTA.code())
                .orElseGet(() -> roleRepository.save(Role.builder().code(Roles.BARISTA.code()).name("Pha chế").build()));

        // Thu ngân PIN-only (username & passwordHash đều null)
        User cashierOk = userRepository.save(User.builder()
                .branchId(branch.getId())
                .roleId(cashierRole.getId())
                .fullName("Thu ngân PIN " + seq)
                .pinHash(pinHasher.hash("2468"))
                .pinLookupHash(pinHasher.lookupHash(branch.getId(), "2468"))
                .position(Roles.CASHIER)
                .status(UserStatus.ACTIVE)
                .build());

        // Thu ngân khóa do sai PIN ở test này
        User cashierLockTarget = userRepository.save(User.builder()
                .branchId(branch.getId())
                .roleId(cashierRole.getId())
                .fullName("Thu ngân Lock " + seq)
                .pinHash(pinHasher.hash("1357"))
                .pinLookupHash(pinHasher.lookupHash(branch.getId(), "1357"))
                .position(Roles.CASHIER)
                .status(UserStatus.ACTIVE)
                .build());

        // Pha chế (không phải CASHIER -> không được xuất hiện và bị 403 nếu cố pin-login)
        User barista = userRepository.save(User.builder()
                .branchId(branch.getId())
                .roleId(baristaRole.getId())
                .fullName("Pha chế PIN " + seq)
                .pinHash(pinHasher.hash("9991"))
                .pinLookupHash(pinHasher.lookupHash(branch.getId(), "9991"))
                .position(Roles.BARISTA)
                .status(UserStatus.ACTIVE)
                .build());

        // 1. Public list cashiers chỉ trả 2 Cashier ACTIVE của chi nhánh
        mockMvc.perform(get("/api/v1/auth/cashiers?branchId=" + branch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].employeeId", hasItem(cashierOk.getId())))
                .andExpect(jsonPath("$.data[*].hasPin", hasItem(true)));

        // 2. Cashier PIN-only login thành công -> nhận JWT, refreshToken = null, gọi /me bình thường
        MvcResult pinLoginRes = mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchId":%d,"employeeId":%d,"pin":"2468"}
                                """.formatted(branch.getId(), cashierOk.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("CASHIER"))
                .andExpect(jsonPath("$.data.user.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.data.landing").value("SCR-04"))
                .andReturn();
        JsonNode pinJson = objectMapper.readTree(pinLoginRes.getResponse().getContentAsString());
        assertThat(pinJson.at("/data/refreshToken").isNull() || pinJson.at("/data/refreshToken").isMissingNode()).isTrue();
        String cashierAccessToken = pinJson.at("/data/accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + cashierAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CASHIER"))
                .andExpect(jsonPath("$.data.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.data.permissions", hasItem("SHIFT_OPEN")))
                .andExpect(jsonPath("$.data.permissions", org.hamcrest.Matchers.not(hasItem("CASH_IN_OUT"))));

        // 3. Nhân viên không phải CASHIER -> 403 ACCESS_DENIED
        mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchId":%d,"employeeId":%d,"pin":"9991"}
                                """.formatted(branch.getId(), barista.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 4. Chọn Cashier khác chi nhánh -> 403 CROSS_BRANCH_DENIED
        mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchId":%d,"employeeId":%d,"pin":"2468"}
                                """.formatted(otherBranch.getId(), cashierOk.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_BRANCH_DENIED"));

        // 5. Nhập sai PIN 3 lần -> 401 PIN_LOCKED + Retry-After, sau đó nhập PIN đúng vẫn bị PIN_LOCKED
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/pin-login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"branchId":%d,"employeeId":%d,"pin":"000%d"}
                                    """.formatted(branch.getId(), cashierLockTarget.getId(), i)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_PIN"));
        }

        mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchId":%d,"employeeId":%d,"pin":"9999"}
                                """.formatted(branch.getId(), cashierLockTarget.getId())))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("PIN_LOCKED"));

        mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchId":%d,"employeeId":%d,"pin":"1357"}
                                """.formatted(branch.getId(), cashierLockTarget.getId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PIN_LOCKED"));
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
