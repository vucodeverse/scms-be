package edu.fpt.sba.scms.integration.printer;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cổng giao tiếp máy in hóa đơn/phiếu pha chế qua giao thức ESC/POS (BR-31, SDD).
 */
public interface PrinterPort {

    boolean printReceipt(ReceiptCommand command);

    record ReceiptCommand(
            Integer orderId,
            String receiptNo,
            String branchName,
            String cashierName,
            BigDecimal totalAmount,
            Instant printedAt
    ) {
    }
}

