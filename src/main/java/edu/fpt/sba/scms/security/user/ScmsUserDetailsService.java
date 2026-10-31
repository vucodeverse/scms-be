package edu.fpt.sba.scms.security.user;

import edu.fpt.sba.scms.dto.UserPrincipal;
import edu.fpt.sba.scms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nạp user theo username để {@link org.springframework.security.authentication.dao.DaoAuthenticationProvider}
 * kiểm mật khẩu (BCrypt). Không trả thông tin nếu user INACTIVE -> BadCredentials như user không tồn tại.
 */
@Service
@RequiredArgsConstructor
public class ScmsUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findWithRoleByUsername(username)
                .filter(u -> u.isActive() && u.getPasswordHash() != null)
                .map(u -> new UserPrincipal(u.getId(), u.getUsername(), u.getFullName(),
                        u.roleCode(), u.getBranchId(), null))
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản."));
    }
}
