package edu.fpt.sba.scms.model;

import edu.fpt.sba.scms.common.enums.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * Bảng {@code category} (IMPLEMENTATION.md §7.1 #4) — UC09.
 * Không đặt UNIQUE trên {@code code}: mã tự sinh CAT + id, cần id có sau khi insert (xem {@code CategoryServiceImpl}).
 */
@Entity
@Table(name = "category")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category extends AuditableEntity {

    /** Danh mục hứng sản phẩm bị gỡ khỏi danh mục khác — seed sẵn, không cho sửa/xoá. */
    public static final String UNCATEGORIZED_CODE = "UNCAT";
    private static final String AUTO_CODE_PREFIX = "CAT";
    /** Mã tạm ghi lúc INSERT; không trùng mã thật nào (CAT + id > 0). */
    private static final String PENDING_CODE = "PENDING";

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "category_id")
    private Integer id;

    /**
     * Mã tự sinh CAT + id. Vì id chỉ có sau INSERT (IDENTITY) nên lúc persist ghi {@link #PENDING_CODE},
     * sau đó {@link #assignGeneratedCode()} đổi sang mã thật — cùng một transaction.
     */
    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordStatus status;

    /** INSERT: cột {@code code} NOT NULL — đặt mã tạm trước khi có id. */
    @PrePersist
    void assignPendingCode() {
        if (code == null) {
            code = PENDING_CODE;
        }
    }

    /** Nhận mã tự sinh từ id sau khi id được DB cấp — không cho client nhập mã. */
    public void assignGeneratedCode() {
        this.code = AUTO_CODE_PREFIX + id;
    }

    /** UNCATEGORIZED là hệ thống: không đổi tên/trạng thái, không xoá (sản phẩm sẽ không còn danh mục). */
    public boolean isSystemCategory() {
        return UNCATEGORIZED_CODE.equals(code);
    }
}
