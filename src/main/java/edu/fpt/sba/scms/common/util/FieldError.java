package edu.fpt.sba.scms.common.util;

/**
 * Cặp field + thông điệp lỗi validation ở tầng common (để common không phụ thuộc dto).
 * {@code dto.FieldErrorItem} là bản dùng cho JSON response.
 */
public record FieldError(String field, String message) {
}
