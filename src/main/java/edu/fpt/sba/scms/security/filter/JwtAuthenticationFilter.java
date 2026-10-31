package edu.fpt.sba.scms.security.filter;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.exception.UnauthorizedException;
import edu.fpt.sba.scms.repository.AuthTokenRepository;
import edu.fpt.sba.scms.security.jwt.JwtTokenProvider;
import edu.fpt.sba.scms.dto.UserPrincipal;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bước 3 (IMPLEMENTATION.md §5.4 / §17 #9 — tên DUY NHẤT): Bearer access token -> UserPrincipal.
 * <p>
 * Token hết hạn/sai/không phải ACCESS -> xoá context để filter chain trả 401 AUTH_REQUIRED.
 * Đăng xuất -> thêm jti vào danh sách thu hồi.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTH_ERROR_ATTR = "authErrorCode";

    private final JwtTokenProvider tokenProvider;
    private final AuthTokenRepository authTokenRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                Claims claims = tokenProvider.parseAccess(token);
                if (!JwtTokenProvider.isType(claims, JwtTokenProvider.TYPE_ACCESS)) {
                    throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_INVALID);
                }
                UserPrincipal principal = new UserPrincipal(
                        JwtTokenProvider.userId(claims),
                        claims.getSubject(),
                        claims.get(JwtTokenProvider.CLAIM_NAME_KEY, String.class),
                        claims.get(JwtTokenProvider.CLAIM_ROLE_KEY, String.class),
                        JwtTokenProvider.branchId(claims),
                        claims.getId());
                if (!authTokenRepository.existsRevokedAccessJti(principal.jti())) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (UnauthorizedException e) {
                // Không set Authentication -> entry point trả 401. Không log token.
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_ERROR_ATTR, e.getErrorCode().name());
            }
        }
        chain.doFilter(request, response);
    }
}
