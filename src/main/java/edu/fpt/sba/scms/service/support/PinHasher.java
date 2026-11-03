package edu.fpt.sba.scms.service.support;

import edu.fpt.sba.scms.common.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PIN kiosk 4 số — IMPLEMENTATION.md §7.2:
 * <ul>
 *   <li>{@code pin_hash = bcrypt(pin)} — không đảo ngược được, dùng để <b>kiểm</b> khi check-in.</li>
 *   <li>{@code pin_lookup_hash = SHA-256(branchId:pin:pepper)} — dùng cho unique index
 *       {@code UNIQUE(branch_id, pin_lookup_hash)} (không query được trên bcrypt).</li>
 * </ul>
 * Không bao giờ trả/log PIN raw.
 */
@RequiredArgsConstructor
public class PinHasher {

    private final PasswordEncoder passwordEncoder;
    private final String pepper;

    public String hash(String pin) {
        return passwordEncoder.encode(pin);
    }

    public boolean matches(String rawPin, String pinHash) {
        return pinHash != null && passwordEncoder.matches(rawPin, pinHash);
    }

    /** {@code SHA-256(branchId + ':' + pin + pepper)} — cùng branch + PIN -> cùng hash (để UNIQUE). */
    public String lookupHash(Integer branchId, String pin) {
        return SecurityUtil.sha256Hex(branchId + ":" + pin + ":" + pepper);
    }
}