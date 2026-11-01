package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Race condition của refresh rotation: nhiều request đồng thời cùng một refresh token thì <b>chỉ một</b> được
 * cấp cặp token mới (DB thu hồi nguyên tử), phần còn lại 401.
 * <p>
 * Dùng user riêng để không ảnh hưởng test khác và để cô lập phạm vi "huỷ toàn bộ phiên" khi replay.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthConcurrencyIT {

    private static final String PASSWORD = "Race@12345";
    private static final int THREADS = 6;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void concurrentRefreshWithSameTokenIssuesExactlyOnePair() throws Exception {
        String username = "race_user_" + System.nanoTime();
        createUser(username);
        String refreshToken = objectMapper.readTree(login(username)).at("/data/refreshToken").asText();

        List<Integer> statuses = fireConcurrently(THREADS, () -> refreshStatus(refreshToken));

        long ok = statuses.stream().filter(s -> s == 200).count();
        long unauthorized = statuses.stream().filter(s -> s == 401).count();
        assertThat(ok).as("chỉ một request được rotation: %s", statuses).isEqualTo(1);
        assertThat(unauthorized).isEqualTo(THREADS - 1);
    }

    // ------------------------------------------------------------------ helpers

    private List<Integer> fireConcurrently(int threads, Callable<Integer> action) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return action.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private int refreshStatus(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private String login(String username) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","rememberMe":true}
                                """.formatted(username, PASSWORD)))
                .andReturn().getResponse().getContentAsString();
    }

    private void createUser(String username) {
        Role role = roleRepository.findByCode(Roles.BARISTA.code()).orElseGet(() ->
                roleRepository.save(Role.builder().code(Roles.BARISTA.code()).name("Pha chế").build()));
        Instant now = Instant.now();
        userRepository.save(User.builder()
                .username(username)
                .fullName("Race Test")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .roleId(role.getId())
                .position(Roles.BARISTA)
                .status(UserStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    /** Dùng cho debug khi test fail — in envelope để đọc errorCode. */
    @SuppressWarnings("unused")
    private JsonNode parse(String body) {
        return objectMapper.readTree(body);
    }
}
