package edu.fpt.sba.scms.dto.request;

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
 * Request payload tạo phiếu hủy nguyên vật liệu hỏng / hết hạn (UC30 §5.2).
 */
@Schema(name = "CreateDisposalSlipRequest", description = "Dữ liệu tạo phiếu hủy nguyên vật liệu hỏng, rách bao bì, quá hạn sử dụng")
public record CreateDisposalSlipRequest(
        @Schema(description = "Phân loại nguyên nhân hủy: COOLING_FAILURE, EXPIRED, PACKAGING_DAMAGE, DEGRADATION", example = "COOLING_FAILURE")
        @NotBlank(message = "Phân loại nguyên nhân không được để trống.")
        @Pattern(regexp = "^(COOLING_FAILURE|EXPIRED|PACKAGING_DAMAGE|DEGRADATION)$",
                message = "Phân loại nguyên nhân hủy phải thuộc: COOLING_FAILURE, EXPIRED, PACKAGING_DAMAGE, hoặc DEGRADATION.")
        String reasonCategory,

        @Schema(description = "Lý do giải trình chi tiết bắt buộc (tối thiểu 10 ký tự, BR-43)", example = "Sữa tươi Dalat Milk lô 01/11 bị chua do hỏng lốc làm mát tủ bảo quản đêm 06/11")
        @NotBlank(message = "Lý do hủy hàng không được để trống.")
        @Size(min = 10, max = 500, message = "Lý do hủy hàng bắt buộc phải mô tả chi tiết nguyên nhân (tối thiểu 10 ký tự) theo quy định BR-43.")
        String reason,

        @Schema(description = "Đường dẫn ảnh chụp hiện trường / hàng hỏng bằng chứng", example = "uploads/evidence/20261107_spoiled_milk_lot01.jpg")
        @Size(max = 255, message = "Đường dẫn ảnh bằng chứng tối đa 255 ký tự.")
        String evidenceFileId,

        @Schema(description = "Danh sách các mặt hàng xin hủy (tối thiểu 1 món)")
        @NotEmpty(message = "Danh sách mặt hàng hủy không được để trống.")
        @Valid
        List<Item> items
) {

    @Schema(name = "CreateDisposalSlipItem", description = "Chi tiết một mặt hàng trong phiếu hủy")
    public record Item(
            @Schema(description = "Mã nguyên vật liệu", example = "8")
            @NotNull(message = "Mã nguyên vật liệu không được để trống.")
            Long ingredientId,

            @Schema(description = "Số lượng kiện / thùng nguyên xin hủy (> 0)", example = "2.0")
            @NotNull(message = "Số lượng kiện hủy không được để trống.")
            @DecimalMin(value = "0.01", message = "Số lượng kiện hủy phải lớn hơn 0.")
            BigDecimal quantityPacks,

            @Schema(description = "Chi tiết tình trạng cụ thể của mặt hàng", example = "Hộp sữa bị phồng nắp, sữa chua đặc")
            @Size(max = 255, message = "Mô tả tình trạng mặt hàng tối đa 255 ký tự.")
            String itemCause
    ) {
    }
}
