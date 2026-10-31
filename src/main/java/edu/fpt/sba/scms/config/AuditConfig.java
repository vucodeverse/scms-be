package edu.fpt.sba.scms.config;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình audit.
 * <p>
 * Giai đoạn hiện tại (chưa có bảng {@code audit_log}) dùng implementation ghi log có cấu trúc;
 * khi thêm entity {@code AuditLog} + repository, tạo bean {@code AuditLogService} thật —
 * bean này tự bị thay thế nhờ {@code @ConditionalOnMissingBean}.
 */
@Slf4j
@Configuration
public class AuditConfig {

    @Bean
    @ConditionalOnMissingBean(AuditLogService.class)
    public AuditLogService loggingAuditLogService() {
        return (action, entity, entityId, before, after) ->
                log.info("AUDIT action={} entity={} entityId={}", action, entity, entityId);
    }
}
