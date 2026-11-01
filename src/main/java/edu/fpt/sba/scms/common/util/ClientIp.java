package edu.fpt.sba.scms.common.util;

import java.net.InetAddress;
import java.util.Optional;

/**
 * Lấy IP client để làm khoá rate-limit. Chỉ nên gọi khi <b>chưa xác định được người dùng</b>.
 * <p>
 * Lưu ý vận hành: {@code X-Forwarded-For} do client tự gửi nên <b>không tin được</b> nếu app không nằm sau
 * reverse proxy đã ghi đè header ({@code server.forward-headers-strategy=framework} chỉ áp cho
 * {@code request.getRemoteAddr()} của servlet container). Vì vậy header chỉ được đọc khi cấu hình bật tường minh.
 * <p>
 * IP được chuẩn hoá: IPv4/IPv6 -> dạng literal ổn định, toàn bộ IPv6 trong /64 gom chung (một máy thường sở hữu
 * cả /64 nên đổi địa chỉ liên tục không lách được rate-limit). Không dùng getHostName -> tránh DNS lookup.
 */
public final class ClientIp {

    static final String UNKNOWN = "unknown";

    private ClientIp() {
    }

    /**
     * @param remoteAddr     {@code HttpServletRequest#getRemoteAddr()}
     * @param forwardedFor   giá trị header {@code X-Forwarded-For} (có thể null)
     * @param trustForwarded true khi app chạy sau proxy đáng tin cậy
     */
    public static String fromHeader(String remoteAddr, String forwardedFor, boolean trustForwarded) {
        if (trustForwarded && forwardedFor != null && !forwardedFor.isBlank()) {
            // Phần tử NGOÀI CÙNG BÊN PHẢI là IP do proxy gần nhất thêm vào; phần bên trái client tự bịa được.
            String[] parts = forwardedFor.split(",");
            return normalize(parts[parts.length - 1].trim());
        }
        return normalize(remoteAddr);
    }

    /** Chuẩn hoá địa chỉ IP về khoá ổn định; null/không parse được -> {@link #UNKNOWN}. */
    public static String normalize(String ip) {
        if (ip == null || ip.isBlank()) {
            return UNKNOWN;
        }
        String value = ip.trim();
        int zone = value.indexOf('%');
        if (zone > 0) {
            value = value.substring(0, zone);
        }
        Optional<InetAddress> parsed = parseLiteral(value);
        if (parsed.isEmpty()) {
            return UNKNOWN;
        }
        byte[] raw = parsed.get().getAddress();
        if (raw.length == 16) {
            return ipv6Prefix64(raw);
        }
        return (raw[0] & 0xff) + "." + (raw[1] & 0xff) + "." + (raw[2] & 0xff) + "." + (raw[3] & 0xff);
    }

    private static Optional<InetAddress> parseLiteral(String value) {
        // Chỉ nhận literal: chữ số + dấu chấm hoặc ký tự hex của IPv6. Tên host không được tra DNS.
        if (!value.matches("[0-9A-Fa-f:.]+")) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(value));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Gom IPv6 theo /64: 8 byte đầu, bỏ phần host do client tự đổi. */
    private static String ipv6Prefix64(byte[] raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i > 0 && i % 2 == 0) {
                sb.append(':');
            }
            sb.append(String.format("%02x", raw[i]));
        }
        return sb.append("::/64").toString();
    }
}
