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
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Bảng {@code product_topping} (IMPLEMENTATION.md §7.1 #8) — UC11.
 * Khoá chép (productId, toppingId); liên kết ghi/xoá theo từng dòng vì danh sách thay đổi theo lần gửi.
 */
@Entity
@Table(name = "product_topping")
@IdClass(ProductTopping.Key.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductTopping {

    @Id
    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Id
    @Column(name = "topping_id", nullable = false)
    private Integer toppingId;

    /** Giá cộng thêm khi chọn topping cho sản phẩm này (VND). */
    @Column(name = "extra_price", nullable = false, precision = 19, scale = 0)
    private BigDecimal extraPrice;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    /** Khoá tổng hợp của bảng liên kết. */
    public record Key(Integer productId, Integer toppingId) implements Serializable {
        public Key {
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(toppingId, "toppingId");
        }
    }
}
