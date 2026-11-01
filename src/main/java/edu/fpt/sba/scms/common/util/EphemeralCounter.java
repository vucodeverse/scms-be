package edu.fpt.sba.scms.common.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Bộ đếm "cửa sổ trượt" lưu in-memory, tự xoá entry hết hạn ngay trong thao tác ghi <b>và</b> có method
 * {@code purge*} cho sweeper định kỳ.
 * <p>
 * Dùng chung cho {@code LoginAttemptService} (đếm lần sai) và {@code RefreshRateLimiter} (đếm lần refresh).
 * Hai lớp đó khác kiểu dữ liệu/nghiệp vụ nên không gom thành một lớp, nhưng phần khoá + ngưỡng + giải phóng
 * bộ nhớ giống hệt nhau nên ở đây.
 * <p>
 * Vì sao tự xoá khi thao tác: kẻ tấn công có thể gửi hàng triệu khoá khác nhau (username, token rác) -> map phình
 * tới OOM. Nếu chỉ dựa vào sweeper theo lịch (có thể chạy trễ hoặc job lỗi) thì giữa hai lần sweep vẫn bị đánh sập;
 * nên khi map vượt {@link #HARD_LIMIT} mỗi lần ghi sẽ kèm một lần quét. Sweeper định kỳ chỉ để dọn phần dư cho gọn.
 * <p>
 * <b>Giới hạn:</b> in-memory chỉ đúng cho 1 instance. Khi scale ngang phải thay bằng store chung (Redis).
 */
public final class EphemeralCounter<V> {

    /** Ngưỡng: vượt mức này, mỗi lần ghi kèm một lần quét hết hạn để map không phình thêm. */
    static final int HARD_LIMIT = 10_000;

    private final ConcurrentHashMap<String, V> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public EphemeralCounter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Ghi/đọc nguyên tử entry của {@code key}. {@code expiryOf} cho biết mốc thời gian rồi bỏ của entry hiện có:
     * đã qua mốc -> {@code remapping} nhận {@code null} như entry mới.
     */
    public V compute(String key, Function<V, Instant> expiryOf, UnaryOperator<V> remapping) {
        if (entries.size() >= HARD_LIMIT) {
            purgeExpired(expiryOf);
        }
        Instant now = clock.instant();
        return entries.compute(key, (k, existing) -> {
            Instant expiredAt = existing == null ? null : expiryOf.apply(existing);
            boolean expired = expiredAt != null && !expiredAt.isAfter(now);
            return remapping.apply(expired ? null : existing);
        });
    }

    public V get(String key) {
        return entries.get(key);
    }

    public void remove(String key) {
        entries.remove(key);
    }

    /** Xoá entry có mốc {@code expiryOf} đã qua (hoặc bằng) hiện tại; trả số entry bị xoá. */
    public int purgeExpired(Function<V, Instant> expiryOf) {
        Instant now = clock.instant();
        int before = entries.size();
        entries.entrySet().removeIf(e -> {
            Instant expiry = expiryOf.apply(e.getValue());
            return expiry != null && !expiry.isAfter(now);
        });
        return before - entries.size();
    }

    public int size() {
        return entries.size();
    }

    /** Chỉ dùng cho test. */
    void clear() {
        entries.clear();
    }
}
