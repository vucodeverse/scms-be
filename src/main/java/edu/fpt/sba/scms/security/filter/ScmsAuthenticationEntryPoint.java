package edu.fpt.sba.scms.security.filter;

import edu.fpt.sba.scms.config.TraceContextFilter;
import tools.jackson.databind.ObjectMapper;
import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 401 khi chưa xác thực, trả ĐÚNG envelope ApiErrorResponse (IMPLEMENTATION.md §8.2). */
@Component
@RequiredArgsConstructor
public class ScmsAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object attr = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR);
        ErrorCode code = attr instanceof String name ? ErrorCode.valueOf(name) : ErrorCode.AUTH_REQUIRED;
        write(response, request, objectMapper, code);
    }

    static void write(HttpServletResponse response, HttpServletRequest request, ObjectMapper mapper,
                      ErrorCode code) throws IOException {
        ApiErrorResponse body = ApiErrorResponse.of(HttpStatus.UNAUTHORIZED, code,
                request.getRequestURI(), TraceContextFilter.currentTraceId(), null);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(mapper.writeValueAsBytes(body));
    }
}
