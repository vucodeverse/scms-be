package edu.fpt.sba.scms.security.filter;

import tools.jackson.databind.ObjectMapper;
import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.trace.TraceContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/** 403 khi đủ danh tính nhưng thiếu quyền — cùng envelope lỗi. */
@Component
@RequiredArgsConstructor
public class ScmsAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ErrorCode code = ErrorCode.ACCESS_DENIED;
        ApiErrorResponse body = new ApiErrorResponse(false, HttpServletResponse.SC_FORBIDDEN, code.name(),
                null, code.message(), request.getRequestURI(), Instant.now(),
                TraceContextFilter.currentTraceId(), null);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(objectMapper.writeValueAsBytes(body));
    }
}
