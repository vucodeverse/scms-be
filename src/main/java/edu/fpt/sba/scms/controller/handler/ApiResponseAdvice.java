package edu.fpt.sba.scms.controller.handler;

import edu.fpt.sba.scms.common.trace.TraceIds;
import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Instant;

/**
 * Tự bọc ApiResponse cho mọi response thường (IMPLEMENTATION.md §5.5).
 * Bỏ qua: lỗi (GlobalExceptionHandler đã bọc), file/stream, {@code ResponseEntity} (controller tự quyết HTTP status),
 * Swagger/springdoc, actuator.
 */
@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    /** Package của các endpoint không được bọc (springdoc / actuator / lỗi mặc định). */
    private static final String[] SKIPPED_PACKAGES = {
            "org.springdoc.", "org.springframework.boot.actuate."
    };

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> type = returnType.getParameterType();
        if (ApiResponse.class.isAssignableFrom(type)
                || ApiErrorResponse.class.isAssignableFrom(type)
                || ResponseEntity.class.isAssignableFrom(type)
                || Resource.class.isAssignableFrom(type)
                || StreamingResponseBody.class.isAssignableFrom(type)
                || byte[].class.equals(type)) {
            return false;
        }
        // Dựa vào package của handler thay vì path: springdoc/actuator có thể đổi path qua config.
        String declaring = returnType.getDeclaringClass().getPackageName();
        for (String prefix : SKIPPED_PACKAGES) {
            if (declaring.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        String path = request.getURI().getPath();
        // /error do BasicErrorController xử lý (package khác) — chặn dự phòng khi advice bị gọi.
        if (path.startsWith("/error")) {
            return body;
        }
        if (body instanceof ApiResponse<?> || body instanceof ApiErrorResponse) {
            return body;
        }
        int status = response instanceof ServletServerHttpResponse servlet && servlet.getServletResponse() != null
                ? servlet.getServletResponse().getStatus()
                : HttpStatus.OK.value();
        return new ApiResponse<>(true, status, "SUCCESS", "OK", body,
                Instant.now(), TraceIds.current());
    }
}
