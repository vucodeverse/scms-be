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

/** Bảng {@code voucher_product} (IMPLEMENTATION.md §7.1 #28) — UC13: phạm vi sản phẩm của voucher. */
@Entity
@Table(name = "voucher_product")
@IdClass(VoucherProduct.Key.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherProduct {

    @Id
    @Column(name = "voucher_id", nullable = false)
    private Integer voucherId;

    @Id
    @Column(name = "product_id", nullable = false)
    private Integer productId;

    public record Key(Integer voucherId, Integer productId) implements Serializable {
        public Key {
            Objects.requireNonNull(voucherId, "voucherId");
            Objects.requireNonNull(productId, "productId");
        }
    }
}
