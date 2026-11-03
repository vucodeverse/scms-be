package edu.fpt.sba.scms.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import edu.fpt.sba.scms.common.enums.BranchStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * Body {@code POST /branches} và {@code PUT /branches/{id}} (IMPLEMENTATION.md §10.3).
 * Không có {@code code}: tạo qua {@link CreateBranchRequest}, sửa thì mã read-only.
 * Điều kiện {@code openingTime < closingTime} kiểm ở service (cần so hai field).
 */
@Schema(name = "SaveBranchRequest")
public record SaveBranchRequest(
        @NotBlank(message = "Tên chi nhánh là bắt buộc.")
        @Size(max = 100, message = "Tên chi nhánh tối đa 100 ký tự.")
        String name,

        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự.")
        String address,

        @Pattern(regexp = "^0\\d{9,10}$", message = "Số điện thoại phải bắt đầu bằng 0 và gồm 10-11 số.")
        String phone,

        @Email(message = "Email không hợp lệ.")
        @Size(max = 255, message = "Email tối đa 255 ký tự.")
        String email,

        @Size(max = 20, message = "Mã số thuế tối đa 20 ký tự.")
        String taxCode,

        @NotNull(message = "Giờ mở cửa là bắt buộc.")
        @JsonFormat(pattern = "HH:mm")
        @Schema(type = "string", example = "07:00")
        LocalTime openingTime,

        @NotNull(message = "Giờ đóng cửa là bắt buộc.")
        @JsonFormat(pattern = "HH:mm")
        @Schema(type = "string", example = "22:00")
        LocalTime closingTime,

        @Min(value = 0, message = "Số bàn phải >= 0.")
        @Max(value = 10_000, message = "Số bàn tối đa 10000.")
        Integer tableCount,

        @NotNull(message = "Trạng thái là bắt buộc.")
        BranchStatus status) {
}
