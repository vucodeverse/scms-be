package edu.fpt.sba.scms.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Mốc thời gian chung của master data (IMPLEMENTATION.md §7.0: {@code created_at} NOT NULL, {@code updated_at}
 * do Hibernate cập nhật — không dùng trigger để test dễ).
 * <p>
 * Cần vì master data có thao tác XOÁ CỨNG (theo yêu cầu) và bảng lịch sử gói đồng bộ chỉ lưu lịch sử
 * publish, không lưu lịch sử mọi lần sửa; {@code updatedAt} giúp đối soát khi có sự cố dữ liệu.
 * Không dùng cho entity đã có cột thời gian riêng (vd. {@code User}).
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditableEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void stampCreatedAt() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void stampUpdatedAt() {
        updatedAt = Instant.now();
    }
}
