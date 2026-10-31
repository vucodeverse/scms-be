package edu.fpt.sba.scms.security.filter;

import edu.fpt.sba.scms.common.BranchContext;
import edu.fpt.sba.scms.dto.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bước 4 của filter chain (IMPLEMENTATION.md §5.4): đưa userId/role/branchId vào BranchContext (ThreadLocal)
 * và MDC; <b>luôn clear trong finally</b>.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class BranchContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            BranchContext.set(new BranchContext.Info(principal.userId(), principal.role(), principal.branchId()));
            if (principal.userId() != null) {
                MDC.put("userId", String.valueOf(principal.userId()));
            }
            if (principal.branchId() != null) {
                MDC.put("branchId", String.valueOf(principal.branchId()));
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            BranchContext.clear();
            MDC.remove("userId");
            MDC.remove("branchId");
        }
    }
}
