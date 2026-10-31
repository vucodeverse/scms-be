package edu.fpt.sba.scms.common;

import edu.fpt.sba.scms.common.exception.ForbiddenException;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.security.Roles;

import java.util.Optional;

/**
 * Phạm vi chi nhánh của request hiện tại (IMPLEMENTATION.md §8.5).
 * <b>Bắt buộc</b> {@link #clear()} trong finally — xem {@code BranchContextFilter}.
 */
public final class BranchContext {

    public record Info(Integer userId, String role, Integer branchId) {

        public boolean isCoo() {
            return Roles.COO.equals(role);
        }
    }

    private static final ThreadLocal<Info> CURRENT = new ThreadLocal<>();

    private BranchContext() {
    }

    public static void set(Info info) {
        CURRENT.set(info);
    }

    public static Optional<Info> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** COO có thể null; role/branch khác phải có branchId, nếu không -> 403 CROSS_BRANCH_DENIED. */
    public static Integer requireBranchId() {
        Info info = CURRENT.get();
        if (info == null || info.branchId() == null) {
            throw new ForbiddenException(ErrorCode.CROSS_BRANCH_DENIED);
        }
        return info.branchId();
    }

    /**
     * Chọn chi nhánh để truy vấn: COO dùng requested; các role khác luôn dùng branch trong token,
     * nếu client gửi khác -> 403 (IMPLEMENTATION.md §8.5).
     */
    public static Integer resolveBranchScope(Integer requestedBranchId) {
        Info info = CURRENT.get();
        if (info == null) {
            throw new ForbiddenException(ErrorCode.CROSS_BRANCH_DENIED);
        }
        if (info.isCoo()) {
            return requestedBranchId;
        }
        if (requestedBranchId != null && !requestedBranchId.equals(info.branchId())) {
            throw new ForbiddenException(ErrorCode.CROSS_BRANCH_DENIED);
        }
        return requireBranchId();
    }
}
