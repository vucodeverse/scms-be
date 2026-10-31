package edu.fpt.sba.scms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lỗi theo từng field của request")
public record FieldErrorItem(
        @Schema(example = "username") String field,
        @Schema(example = "Tên đăng nhập là bắt buộc.") String message) {
}
