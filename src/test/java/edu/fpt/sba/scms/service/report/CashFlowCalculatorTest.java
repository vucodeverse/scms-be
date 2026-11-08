package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.enums.CashReconcileStatus;
import edu.fpt.sba.scms.common.enums.CashTransactionType;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.dto.response.report.CashDrawerDto;
import edu.fpt.sba.scms.dto.response.report.CashFlowSummaryDto;
import edu.fpt.sba.scms.dto.response.report.CashMovementDto;
import edu.fpt.sba.scms.dto.response.report.ShiftCashFlowDto;
import edu.fpt.sba.scms.model.CashTransaction;
import edu.fpt.sba.scms.model.Shift;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CashFlowCalculatorTest {

    private static CashTransaction tx(CashTransactionType type, String amount) {
        return CashTransaction.builder().shiftId(1).type(type).amount(new BigDecimal(amount)).reason("lý do").build();
    }

    private static Shift shift(ShiftStatus status, String counted) {
        return Shift.builder().id(1).status(status)
                .openingCash(new BigDecimal("500000"))
                .cashRevenue(new BigDecimal("100000"))
                .countedCash(counted == null ? null : new BigDecimal(counted))
                .discrepancyReason("Thiếu tiền lẻ")
                .build();
    }

    private final List<CashTransaction> txs = List.of(
            tx(CashTransactionType.OPEN, "500000"),
            tx(CashTransactionType.CASH_IN, "50000"),
            tx(CashTransactionType.CASH_OUT, "20000"),
            tx(CashTransactionType.REFUND_OUT, "10000"),
            tx(CashTransactionType.CLOSE, "615000"));

    @Test
    void caDaDongTinhTienDuKienVaChenhLech() {
        ShiftCashFlowDto dto = CashFlowCalculator.shift(shift(ShiftStatus.CLOSED, "615000"), txs);

        // 500k + 100k + 50k - 20k - 10k (BR-39)
        assertThat(dto.expectedCash()).isEqualByComparingTo("620000");
        assertThat(dto.variance()).isEqualByComparingTo("-5000");
        assertThat(dto.reconcileStatus()).isEqualTo(CashReconcileStatus.RECONCILED);
        assertThat(dto.hasVariance()).isTrue();
        assertThat(dto.movements()).extracting(CashMovementDto::type).containsExactly(
                CashTransactionType.CASH_IN, CashTransactionType.CASH_OUT, CashTransactionType.REFUND_OUT);
    }

    @Test
    void caChuaDongThiChuaDoiSoatVaKhongTinhChenhLech() {
        ShiftCashFlowDto dto = CashFlowCalculator.shift(shift(ShiftStatus.OPEN, null), txs);

        assertThat(dto.reconcileStatus()).isEqualTo(CashReconcileStatus.UNRECONCILED);
        assertThat(dto.variance()).isNull();
        assertThat(dto.countedCash()).isNull();
        assertThat(dto.hasVariance()).isFalse();
    }

    @Test
    void tongHopNhieuCa() {
        CashFlowSummaryDto s = CashFlowCalculator.summarize(List.of(
                CashFlowCalculator.shift(shift(ShiftStatus.CLOSED, "615000"), txs),
                CashFlowCalculator.shift(shift(ShiftStatus.OPEN, null), List.of())));

        assertThat(s.shiftCount()).isEqualTo(2);
        assertThat(s.unreconciledCount()).isEqualTo(1);
        assertThat(s.varianceShiftCount()).isEqualTo(1);
        assertThat(s.expectedCash()).isEqualByComparingTo("1220000");
        assertThat(s.totalVariance()).isEqualByComparingTo("-5000");
    }

    @Test
    void ketTienCaDangMo() {
        CashDrawerDto drawer = CashFlowCalculator.drawer(shift(ShiftStatus.OPEN, null), txs);

        assertThat(drawer.shiftOpen()).isTrue();
        assertThat(drawer.expectedCash()).isEqualByComparingTo("620000");
        assertThat(CashDrawerDto.noOpenShift().shiftOpen()).isFalse();
    }
}
