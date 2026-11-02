package edu.fpt.sba.scms.service.support;

/** Chuẩn hoá chuỗi nhập từ client cho master data — giữ một định nghĩa "rỗng" cho mọi entity. */
public final class MasterDataText {

    private MasterDataText() {
    }

    /** Trim; null/chuỗi trắng → null (DB lưu NULL thay vì chuỗi rỗng, tránh trùng lặp dữ liệu hiển thị). */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Như {@link #trimToNull} nhưng bắt buộc có giá trị — dùng cho field {@code @NotBlank} khi lưu. */
    public static String requireText(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException("Chuỗi bắt buộc không được rỗng.");
        }
        return trimmed;
    }
}
