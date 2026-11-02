package edu.fpt.sba.scms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

/** Bảng {@code voucher_category} (UC13, §10.8): phạm vi danh mục của voucher. */
@Entity
@Table(name = "voucher_category")
@IdClass(VoucherCategory.Key.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherCategory {

    @Id
    @Column(name = "voucher_id", nullable = false)
    private Integer voucherId;

    @Id
    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    public record Key(Integer voucherId, Integer categoryId) implements Serializable {
        public Key {
            Objects.requireNonNull(voucherId, "voucherId");
            Objects.requireNonNull(categoryId, "categoryId");
        }
    }
}
