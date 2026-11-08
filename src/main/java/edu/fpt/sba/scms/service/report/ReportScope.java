package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.security.BranchContext;

/**
 * Phạm vi chi nhánh của report (BR-04: BM chỉ xem chi nhánh được giao). Bọc {@link BranchContext#resolveBranchScope}
 * để mọi report áp cùng một rule:
 * <ul>
 *   <li>BM: bỏ trống {@code branchId} → chi nhánh trong token; gửi chi nhánh khác → 403 {@code CROSS_BRANCH_DENIED}
 *       (UC36 AT1).</li>
 *   <li>COO: dùng {@code branchId} được gửi. Với report cấp chi nhánh (UC34/35/37/38) thì bắt buộc phải gửi;
 *       với report cho phép toàn chuỗi (UC41/42) thì {@code null} = toàn chuỗi.</li>
 * </ul>
 * Quyền theo role (ai được gọi report nào) do {@code @PreAuthorize} ở controller chặn, không lặp lại ở đây.
 * Việc chi nhánh COO gửi có tồn tại hay không do service report kiểm (cần repository) — không thuộc lớp này.
 */
public final class ReportScope {

    public static final String MSG_BRANCH_REQUIRED = "Vui lòng chọn chi nhánh.";

    private ReportScope() {
    }

    /**
     * Report cấp chi nhánh: luôn trả về một chi nhánh cụ thể.
     *
     * @throws edu.fpt.sba.scms.common.exception.ForbiddenException 403 khi BM yêu cầu chi nhánh khác / không có context
     * @throws InvalidFieldValueException                           400 khi COO không chọn chi nhánh
     */
    public static Integer requireBranch(Integer requestedBranchId) {
        Integer branchId = BranchContext.resolveBranchScope(requestedBranchId);
        if (branchId == null) {
            throw new InvalidFieldValueException("branchId", MSG_BRANCH_REQUIRED);
        }
        return branchId;
    }

    /**
     * Report cho phép xem toàn chuỗi (UC41, UC42).
     *
     * @return chi nhánh cần lọc, hoặc {@code null} = toàn chuỗi (chỉ COO mới có thể nhận {@code null})
     * @throws edu.fpt.sba.scms.common.exception.ForbiddenException 403 khi BM yêu cầu chi nhánh khác / không có context
     */
    public static Integer branchOrChain(Integer requestedBranchId) {
        return BranchContext.resolveBranchScope(requestedBranchId);
    }

    /** Người gọi hiện tại có phải COO không (vd. UC34 dashboard COO được chọn chi nhánh). */
    public static boolean isCoo() {
        return BranchContext.current().map(BranchContext.Info::isCoo).orElse(false);
    }
}
