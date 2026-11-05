package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.exception.ForbiddenException;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.security.BranchContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportScopeTest {

    private static final int BM_BRANCH = 1;
    private static final int OTHER_BRANCH = 2;

    @AfterEach
    void clear() {
        BranchContext.clear();
    }

    private static void asBranchManager() {
        BranchContext.set(new BranchContext.Info(10, Roles.BRANCH_MANAGER.code(), BM_BRANCH));
    }

    private static void asCoo() {
        BranchContext.set(new BranchContext.Info(1, Roles.COO.code(), null));
    }

    private static void assertCrossBranchDenied(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ForbiddenException.class)
                .satisfies(e -> assertThat(((ForbiddenException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CROSS_BRANCH_DENIED));
    }

    @Test
    void boTrongThiLayChiNhanhTrongToken() {
        asBranchManager();
        assertThat(ReportScope.requireBranch(null)).isEqualTo(BM_BRANCH);
        assertThat(ReportScope.branchOrChain(null)).isEqualTo(BM_BRANCH);
    }

    @Test
    void guiDungChiNhanhCuaMinhThiHopLe() {
        asBranchManager();
        assertThat(ReportScope.requireBranch(BM_BRANCH)).isEqualTo(BM_BRANCH);
    }

    @Test
    void xemChiNhanhKhacBiChan403() {
        asBranchManager();
        assertCrossBranchDenied(() -> ReportScope.requireBranch(OTHER_BRANCH));
        assertCrossBranchDenied(() -> ReportScope.branchOrChain(OTHER_BRANCH));
    }

    @Test
    void khongPhaiCoo() {
        asBranchManager();
        assertThat(ReportScope.isCoo()).isFalse();
    }

    @Test
    void xemDuocChiNhanhBatKy() {
        asCoo();
        assertThat(ReportScope.requireBranch(OTHER_BRANCH)).isEqualTo(OTHER_BRANCH);
        assertThat(ReportScope.branchOrChain(OTHER_BRANCH)).isEqualTo(OTHER_BRANCH);
        assertThat(ReportScope.isCoo()).isTrue();
    }

    @Test
    void reportCapChiNhanhMaKhongChonChiNhanhThi400() {
        asCoo();
        assertThatThrownBy(() -> ReportScope.requireBranch(null))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessage(ReportScope.MSG_BRANCH_REQUIRED);
    }

    @Test
    void reportToanChuoiBoTrongThiLaNull() {
        asCoo();
        assertThat(ReportScope.branchOrChain(null)).isNull();
    }

    @Test
    void khongCoContextThiBiChan403() {
        assertCrossBranchDenied(() -> ReportScope.requireBranch(BM_BRANCH));
        assertCrossBranchDenied(() -> ReportScope.branchOrChain(null));
        assertThat(ReportScope.isCoo()).isFalse();
    }

    @Test
    void bmKhongCoChiNhanhTrongTokenThiBiChan403() {
        BranchContext.set(new BranchContext.Info(10, Roles.BRANCH_MANAGER.code(), null));
        assertCrossBranchDenied(() -> ReportScope.requireBranch(null));
    }
}
