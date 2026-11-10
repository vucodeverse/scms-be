package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.ShiftType;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.util.DateTimeUtil;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.model.ShiftAssignment;
import edu.fpt.sba.scms.model.ShiftAssignmentId;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.ShiftAssignmentRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
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

import java.time.Instant;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
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

        // 0. Chưa có ca ĐANG MỞ và chưa đến ca ĐÃ LÊN LỊCH -> danh sách rỗng & không được mở khóa PIN
        mockMvc.perform(get("/api/v1/auth/cashiers?branchId=" + branch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchId":%d,"employeeId":%d,"pin":"2468"}
                                """.formatted(branch.getId(), cashierOk.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // Lên lịch ca đang trong khung mở ca [now - 30p, now + 4h] với 2 Thu ngân + 1 Pha chế
        Instant now = Instant.now();
        Shift scheduledShift = shiftRepository.save(Shift.builder()
                .branchId(branch.getId())
                .shiftDate(DateTimeUtil.todayVN())
                .shiftType(ShiftType.MORNING)
                .plannedStart(now.minusSeconds(1800))
                .plannedEnd(now.plusSeconds(14400))
                .status(ShiftStatus.SCHEDULED)
                .build());
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(scheduledShift.getId(), cashierOk.getId()))
                .shift(scheduledShift)
                .employee(cashierOk)
                .roleInShift("CASHIER")
                .build());
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(scheduledShift.getId(), cashierLockTarget.getId()))
                .shift(scheduledShift)
                .employee(cashierLockTarget)
                .roleInShift("CASHIER")
                .build());
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(scheduledShift.getId(), barista.getId()))
                .shift(scheduledShift)
                .employee(barista)
                .roleInShift("BARISTA")
                .build());

        // 1. Public list cashiers chỉ trả 2 Cashier được xếp lịch trong ca đang đến giờ
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

    @Autowired
    private edu.fpt.sba.scms.repository.ShiftRepository shiftRepository;

    @Autowired
    private edu.fpt.sba.scms.repository.ShiftAssignmentRepository shiftAssignmentRepository;

    @Test
    void temporaryCashierRoleInShiftIsListedAndCanPinLogin() throws Exception {
        Branch branch = branchRepository.save(Branch.builder()
                .code("BR_TEMP")
                .name("Chi nhánh Gán Role Tạm")
                .status(BranchStatus.OPEN)
                .build());

        Role baristaRole = roleRepository.findByCode("BARISTA").orElseThrow();
        User barista = userRepository.save(User.builder()
                .branchId(branch.getId())
                .roleId(baristaRole.getId())
                .fullName("Pha chế kiêm Thu ngân")
                .pinHash(pinHasher.hash("5555"))
                .pinLookupHash(pinHasher.lookupHash(branch.getId(), "5555"))
                .position(Roles.BARISTA)
                .status(UserStatus.ACTIVE)
                .build());

        // Tạo ca làm việc và gán Barista làm Thu ngân trong ca này (role_in_shift = CASHIER)
        Instant now = Instant.now();
        edu.fpt.sba.scms.model.Shift shift = shiftRepository.save(edu.fpt.sba.scms.model.Shift.builder()
                .branchId(branch.getId())
                .shiftDate(edu.fpt.sba.scms.common.util.DateTimeUtil.todayVN())
                .shiftType(edu.fpt.sba.scms.common.enums.ShiftType.MORNING)
                .plannedStart(now.minusSeconds(1800))
                .plannedEnd(now.plusSeconds(18000))
                .status(edu.fpt.sba.scms.common.enums.ShiftStatus.OPEN)
                .openedAt(now.minusSeconds(1800))
                .build());

        shiftAssignmentRepository.save(edu.fpt.sba.scms.model.ShiftAssignment.builder()
                .id(new edu.fpt.sba.scms.model.ShiftAssignmentId(shift.getId(), barista.getId()))
                .shift(shift)
                .employee(barista)
                .roleInShift("CASHIER")
                .build());

        // 1. Kiểm tra /auth/cashiers trả về nhân viên Barista được gán role tạm CASHIER trong ca này
        mockMvc.perform(get("/api/v1/auth/cashiers?branchId=" + branch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].employeeId", hasItem(barista.getId())))
                .andExpect(jsonPath("$.data[*].fullName", hasItem("Pha chế kiêm Thu ngân")));

        // 2. Barista đăng nhập bằng mã PIN 5555 thành công với vai trò CASHIER
        MvcResult res = mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchId":%d,"employeeId":%d,"pin":"5555"}
                                """.formatted(branch.getId(), barista.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("CASHIER"))
                .andReturn();

        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        String token = json.at("/data/accessToken").asText();

        // 3. /auth/me nhận diện quyền Thu ngân trong phiên này
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CASHIER"))
                .andExpect(jsonPath("$.data.permissions", hasItem("SHIFT_OPEN")));
    }

    @Test
    void pinCashierEligibilityRespectsShiftWindowMultiShiftAndHandoverRelief() throws Exception {
        int seq = SEQ.incrementAndGet();
        Branch branch = branchRepository.save(Branch.builder()
                .code("BR_WIN_" + seq)
                .name("Chi nhánh Gate Giờ Ca " + seq)
                .status(BranchStatus.OPEN)
                .build());

        Role cashierRole = roleRepository.findByCode(Roles.CASHIER.code()).orElseThrow();
        Role baristaRole = roleRepository.findByCode(Roles.BARISTA.code()).orElseThrow();
        Role waiterRole = roleRepository.findByCode(Roles.WAITER.code())
                .orElseGet(() -> roleRepository.save(Role.builder().code(Roles.WAITER.code()).name("Phục vụ").build()));

        User scheduledCashier = userRepository.save(User.builder()
                .branchId(branch.getId())
                .roleId(cashierRole.getId())
                .fullName("Nguyễn Hải")
                .pinHash(pinHasher.hash("1122"))
                .pinLookupHash(pinHasher.lookupHash(branch.getId(), "1122"))
                .position(Roles.CASHIER)
                .status(UserStatus.ACTIVE)
                .build());
        User unscheduledCashier = userRepository.save(User.builder()
                .branchId(branch.getId())
                .roleId(cashierRole.getId())
                .fullName("Thu ngân Ngoài Ca")
                .pinHash(pinHasher.hash("3344"))
                .pinLookupHash(pinHasher.lookupHash(branch.getId(), "3344"))
                .position(Roles.CASHIER)
                .status(UserStatus.ACTIVE)
                .build());
        User tempBaristaCashier = userRepository.save(User.builder()
                .branchId(branch.getId())
                .roleId(baristaRole.getId())
                .fullName("Nguyễn Ánh")
                .pinHash(pinHasher.hash("7788"))
                .pinLookupHash(pinHasher.lookupHash(branch.getId(), "7788"))
                .position(Roles.BARISTA)
                .status(UserStatus.ACTIVE)
                .build());
        User waiterAssignedAsWaiter = userRepository.save(User.builder()
                .branchId(branch.getId())
                .roleId(waiterRole.getId())
                .fullName("Phục vụ Đúng Vai Trò")
                .pinHash(pinHasher.hash("4455"))
                .pinLookupHash(pinHasher.lookupHash(branch.getId(), "4455"))
                .position(Roles.WAITER)
                .status(UserStatus.ACTIVE)
                .build());
        User unassignedWaiter = userRepository.save(User.builder()
                .branchId(branch.getId())
                .roleId(waiterRole.getId())
                .fullName("Phục vụ Chưa Xếp Ca")
                .pinHash(pinHasher.hash("6677"))
                .pinLookupHash(pinHasher.lookupHash(branch.getId(), "6677"))
                .position(Roles.WAITER)
                .status(UserStatus.ACTIVE)
                .build());

        Instant now = Instant.now();

        // 1. Ca sắp tới nhưng còn quá sớm (+90 phút > giới hạn 60 phút) -> KHÔNG hiện danh sách & bị chặn 403
        Shift futureShift = shiftRepository.save(Shift.builder()
                .branchId(branch.getId())
                .shiftDate(DateTimeUtil.todayVN())
                .shiftType(ShiftType.EVENING)
                .plannedStart(now.plusSeconds(5400))
                .plannedEnd(now.plusSeconds(19800))
                .status(ShiftStatus.SCHEDULED)
                .build());
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(futureShift.getId(), scheduledCashier.getId()))
                .shift(futureShift)
                .employee(scheduledCashier)
                .roleInShift("CASHIER")
                .build());
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(futureShift.getId(), tempBaristaCashier.getId()))
                .shift(futureShift)
                .employee(tempBaristaCashier)
                .roleInShift("CASHIER")
                .build());
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(futureShift.getId(), unscheduledCashier.getId()))
                .shift(futureShift)
                .employee(unscheduledCashier)
                .roleInShift("CASHIER")
                .build());

        mockMvc.perform(get("/api/v1/auth/cashiers?branchId=" + branch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchId":%d,"employeeId":%d,"pin":"1122"}
                                """.formatted(branch.getId(), scheduledCashier.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 2. Chi nhánh có 2 Phục vụ nhưng ca SCHEDULED đến giờ chỉ gán 1 Phục vụ kiêm CASHIER (+ 1 Thu ngân gán BARISTA)
        // -> chỉ hiển thị đúng 1 người được gán vai trò CASHIER trong ca đó.
        Shift singleWaiterShift = shiftRepository.save(Shift.builder()
                .branchId(branch.getId())
                .shiftDate(DateTimeUtil.todayVN())
                .shiftType(ShiftType.NIGHT)
                .plannedStart(now.minusSeconds(900))
                .plannedEnd(now.plusSeconds(3600))
                .status(ShiftStatus.SCHEDULED)
                .build());
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(singleWaiterShift.getId(), waiterAssignedAsWaiter.getId()))
                .shift(singleWaiterShift)
                .employee(waiterAssignedAsWaiter)
                .roleInShift("CASHIER")
                .build());
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(singleWaiterShift.getId(), unscheduledCashier.getId()))
                .shift(singleWaiterShift)
                .employee(unscheduledCashier)
                .roleInShift("BARISTA")
                .build());

        mockMvc.perform(get("/api/v1/auth/cashiers?branchId=" + branch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].employeeId").value(waiterAssignedAsWaiter.getId()));

        // Đóng singleWaiterShift để chuyển sang kiểm thử ca đang OPEN
        singleWaiterShift.setStatus(ShiftStatus.CLOSED);
        singleWaiterShift.setClosedAt(now);
        shiftRepository.save(singleWaiterShift);

        // 3. Ca sáng đang OPEN + CHƯA bàn giao két (handoverPending = false, activeCashierId = scheduledCashier):
        // Dù Ca sáng cũng gán Nguyễn Ánh làm CASHIER và Ca chiều (futureShift) đã vào khung +30p với unscheduledCashier,
        // màn PIN chỉ hiển thị DUY NHẤT người đang giữ két (scheduledCashier), chặn 403 mọi người khác chưa tới lượt.
        Shift morningOpenShift = shiftRepository.save(Shift.builder()
                .branchId(branch.getId())
                .shiftDate(DateTimeUtil.todayVN())
                .shiftType(ShiftType.MORNING)
                .plannedStart(now.minusSeconds(3600))
                .plannedEnd(now.plusSeconds(1800))
                .status(ShiftStatus.OPEN)
                .openedAt(now.minusSeconds(3600))
                .activeCashierId(scheduledCashier.getId())
                .handoverPending(false)
                .build());
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(morningOpenShift.getId(), scheduledCashier.getId()))
                .shift(morningOpenShift)
                .employee(scheduledCashier)
                .roleInShift("CASHIER")
                .build());
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(morningOpenShift.getId(), tempBaristaCashier.getId()))
                .shift(morningOpenShift)
                .employee(tempBaristaCashier)
                .roleInShift("CASHIER")
                .build());
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(morningOpenShift.getId(), waiterAssignedAsWaiter.getId()))
                .shift(morningOpenShift)
                .employee(waiterAssignedAsWaiter)
                .roleInShift("WAITER")
                .build());

        futureShift.setPlannedStart(now.plusSeconds(1800));
        futureShift = shiftRepository.save(futureShift);

        mockMvc.perform(get("/api/v1/auth/cashiers?branchId=" + branch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].employeeId").value(scheduledCashier.getId()));

        mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchId":%d,"employeeId":%d,"pin":"7788"}
                                """.formatted(branch.getId(), tempBaristaCashier.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchId":%d,"employeeId":%d,"pin":"3344"}
                                """.formatted(branch.getId(), unscheduledCashier.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // Người đang giữ két vẫn đăng nhập PIN bình thường
        mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchId":%d,"employeeId":%d,"pin":"1122"}
                                """.formatted(branch.getId(), scheduledCashier.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("CASHIER"));

        // 4. Khi Ca sáng chuyển sang chờ bàn giao két (handoverPending = true):
        // Chỉ hiển thị đúng người trong Ca sáng có vai trò CASHIER (scheduledCashier + tempBaristaCashier);
        // KHÔNG lộ unscheduledCashier (kể cả gốc CASHIER hay được xếp ở futureShift),
        // KHÔNG lộ waiterAssignedAsWaiter (gán WAITER) hay unassignedWaiter.
        morningOpenShift.setHandoverPending(true);
        morningOpenShift = shiftRepository.save(morningOpenShift);

        mockMvc.perform(get("/api/v1/auth/cashiers?branchId=" + branch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].employeeId", hasItem(scheduledCashier.getId())))
                .andExpect(jsonPath("$.data[*].employeeId", hasItem(tempBaristaCashier.getId())))
                .andExpect(jsonPath("$.data[*].employeeId", not(hasItem(unscheduledCashier.getId()))))
                .andExpect(jsonPath("$.data[*].employeeId", not(hasItem(waiterAssignedAsWaiter.getId()))))
                .andExpect(jsonPath("$.data[*].employeeId", not(hasItem(unassignedWaiter.getId()))));

        mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchId":%d,"employeeId":%d,"pin":"7788"}
                                """.formatted(branch.getId(), tempBaristaCashier.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("CASHIER"));

        mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchId":%d,"employeeId":%d,"pin":"3344"}
                                """.formatted(branch.getId(), unscheduledCashier.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchId":%d,"employeeId":%d,"pin":"4455"}
                                """.formatted(branch.getId(), waiterAssignedAsWaiter.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 5. Khi Ca sáng CLOSED và Ca chiều đang trong khung giờ mở ca (+30p <= 60p) -> hiện đúng 3 người được gán CASHIER của Ca chiều;
        // sau khi Ca chiều cũng quá hạn (> 30p sau giờ kết thúc) -> danh sách trống hoàn toàn.
        morningOpenShift.setStatus(ShiftStatus.CLOSED);
        morningOpenShift.setClosedAt(now);
        shiftRepository.save(morningOpenShift);

        mockMvc.perform(get("/api/v1/auth/cashiers?branchId=" + branch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[*].employeeId", hasItem(unscheduledCashier.getId())));

        futureShift.setPlannedStart(now.minusSeconds(18000));
        futureShift.setPlannedEnd(now.minusSeconds(3600));
        shiftRepository.save(futureShift);

        mockMvc.perform(get("/api/v1/auth/cashiers?branchId=" + branch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
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
