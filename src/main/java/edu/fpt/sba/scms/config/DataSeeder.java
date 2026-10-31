package edu.fpt.sba.scms.config;

import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.common.enums.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Seed dữ liệu tối thiểu cho DEV (IMPLEMENTATION.md §18.2 V2__seed.sql).
 * Bật bằng {@code app.auth.seed.enabled=true} (profile dev); prod tắt.
 * Chạy sau khi schema đã được Hibernate tạo — idempotent: chỉ chèn khi thiếu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Map<String, String> ROLES = Map.of(
            Roles.COO.code(), "Chief Operations Officer",
            Roles.BRANCH_MANAGER.code(), "Quản lý chi nhánh",
            Roles.CASHIER.code(), "Thu ngân",
            Roles.BARISTA.code(), "Pha chế",
            Roles.WAITER.code(), "Phục vụ");

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Map.Entry<String, String> entry : ROLES.entrySet()) {
            roleRepository.findByCode(entry.getKey()).orElseGet(() -> roleRepository.save(
                    Role.builder().code(entry.getKey()).name(entry.getValue()).build()));
        }

        AppProperties.Auth.Seed seed = appProperties.auth().seed();
        if (seed.cooUsername() == null || userRepository.existsByUsername(seed.cooUsername())) {
            return;
        }
        Role coo = roleRepository.findByCode(Roles.COO.code()).orElseThrow();
        userRepository.save(User.builder()
                .roleId(coo.getId())
                .fullName("COO Demo")
                .email("coo@scms.local")
                .username(seed.cooUsername())
                .passwordHash(passwordEncoder.encode(seed.cooPassword()))
                .position(Roles.COO)
                .status(UserStatus.ACTIVE)
                .build());
        log.warn("Đã tạo tài khoản seed '{}' với mật khẩu lấy từ cấu hình — chỉ dùng cho môi trường DEV.",
                seed.cooUsername());
    }
}
