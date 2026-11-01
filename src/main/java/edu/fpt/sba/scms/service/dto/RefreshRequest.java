package edu.fpt.sba.scms.service.dto;

/**
 * Đầu vào của {@link edu.fpt.sba.scms.service.AuthService#refresh}: token client gửi (body) + token đọc từ cookie.
 * Không nằm trong {@code controller.dto} vì service không được phụ thuộc servlet API.
 *
 * @param fromBody   refresh token trong body của request (có thể null)
 * @param fromCookie refresh token trong cookie httpOnly (có thể null)
 * @param clientIp   IP client đã chuẩn hoá — chỉ dùng khi không đọc được chủ token, để rate-limit theo IP
 */
public record RefreshRequest(String fromBody, String fromCookie, String clientIp) {

    /** Token thực tế sẽ dùng: ưu tiên body, rồi cookie. */
    public String token() {
        return hasText(fromBody) ? fromBody : (hasText(fromCookie) ? fromCookie : null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
