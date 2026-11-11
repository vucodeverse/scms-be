package edu.fpt.sba.scms.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request payload tạo phiếu hủy nguyên vật liệu hỏng / hết hạn (UC30 §5.2, FEA-04: Waste Ticket).
 */
@Schema(name = "CreateDisposalSlipRequest", description = "Dữ liệu tạo phiếu hủy hàng hỏng, quá date, sự cố quầy bar (FEA-04)")
public record CreateDisposalSlipRequest(
        @Schema(description = "Mã chi nhánh lập phiếu (nếu để trống hệ thống sẽ lấy theo người dùng đăng nhập)", example = "4")
        Integer branchId,

        @Schema(description = "Phân loại lý do hủy: BARISTA_ERROR, EXPIRED_SHELF_LIFE, STORAGE_DEFECT, CUSTOMER_COMPLAINT (hoặc mã cũ: COOLING_FAILURE, EXPIRED, PACKAGING_DAMAGE, DEGRADATION)", example = "BARISTA_ERROR")
        @JsonAlias({"reasonCode", "reasonCategory"})
        @NotBlank(message = "Phân loại nguyên nhân hủy không được để trống.")
        @Pattern(regexp = "^(BARISTA_ERROR|EXPIRED_SHELF_LIFE|STORAGE_DEFECT|CUSTOMER_COMPLAINT|COOLING_FAILURE|EXPIRED|PACKAGING_DAMAGE|DEGRADATION)$",
                message = "Phân loại nguyên nhân hủy phải thuộc danh mục lý do hợp lệ.")
        String reasonCategory,

        @Schema(description = "Giải trình chi tiết nguyên nhân hủy bắt buộc (tối thiểu 10 ký tự, BR-43)", example = "Barista làm rơi vỡ chai siro đào trong lúc chuẩn bị ca sáng")
        @JsonAlias({"explanation", "reason"})
        @NotBlank(message = "Lý do giải trình hủy không được để trống.")
        @Size(min = 10, max = 500, message = "Lý do hủy hàng bắt buộc phải mô tả chi tiết nguyên nhân (tối thiểu 10 ký tự).")
        String reason,

        @Schema(description = "Đường dẫn ảnh chụp hiện trường / hàng hỏng bằng chứng (Rule 1)", example = "https://img.scms.local/evidence/20261111-01.jpg")
        @JsonAlias({"evidenceImageUrl", "evidenceFileId"})
        @Size(max = 500, message = "Đường dẫn ảnh bằng chứng tối đa 500 ký tự.")
        String evidenceFileId,

        @Schema(description = "Danh sách các mặt hàng xin hủy (tối thiểu 1 món)")
        @NotEmpty(message = "Danh sách mặt hàng hủy không được để trống.")
        @Valid
        List<Item> items
) {

    public CreateDisposalSlipRequest(String reasonCategory, String reason, String evidenceFileId, List<Item> items) {
        this(null, reasonCategory, reason, evidenceFileId, items);
    }

    public String reasonCode() {
        return reasonCategory;
    }

    public String explanation() {
        return reason;
    }

    public String evidenceImageUrl() {
        return evidenceFileId;
    }

    @Schema(name = "CreateDisposalSlipItem", description = "Chi tiết một mặt hàng trong phiếu hủy")
    public record Item(
            @Schema(description = "Mã nguyên vật liệu", example = "6")
            @NotNull(message = "Mã nguyên vật liệu không được để trống.")
            Long ingredientId,

            @Schema(description = "Số lượng theo đơn vị cơ sở (ml, g, quả, hộp, can...) bỏ đi thực tế (FEA-04)", example = "700")
            @DecimalMin(value = "0.0001", message = "Số lượng cơ sở hủy phải lớn hơn 0.")
            BigDecimal quantityBase,

            @Schema(description = "Số lượng kiện / thùng nguyên xin hủy (> 0)", example = "2.0")
            @DecimalMin(value = "0.01", message = "Số lượng kiện hủy phải lớn hơn 0.")
            BigDecimal quantityPacks,

            @Schema(description = "Chi tiết tình trạng cụ thể của mặt hàng", example = "Chai siro rơi vỡ sàn quầy bar")
            @Size(max = 255, message = "Mô tả tình trạng mặt hàng tối đa 255 ký tự.")
            String itemCause
    ) {

        public Item(Long ingredientId, BigDecimal quantityBase) {
            this(ingredientId, quantityBase, null, null);
        }

        public Item(Long ingredientId, BigDecimal quantityPacks, String itemCause) {
            this(ingredientId, null, quantityPacks, itemCause);
        }
    }
}
