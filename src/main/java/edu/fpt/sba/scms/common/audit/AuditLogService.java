package edu.fpt.sba.scms.common.audit;

import edu.fpt.sba.scms.common.enums.AuditAction;
/**
 * Hợp đồng ghi audit, ghi ĐỒNG BỘ trong cùng transaction nghiệp vụ (IMPLEMENTATION.md §8.4).
 * <p>
 * Triển khai (kèm entity AuditLog) sẽ nằm ở tầng security/audit khi có bảng {@code audit_log};
 * tầng common chỉ giữ hợp đồng để service import mà không phụ thuộc repository.
 */
public interface AuditLogService {

    void log(AuditAction action, String entity, Object entityId, Object before, Object after);
}
