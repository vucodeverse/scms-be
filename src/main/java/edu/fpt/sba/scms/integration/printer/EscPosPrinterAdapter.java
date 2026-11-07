package edu.fpt.sba.scms.integration.printer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter gửi lệnh in tới máy in nhiệt ESC/POS (BR-31).
 */
@Slf4j
@Component
public class EscPosPrinterAdapter implements PrinterPort {

    @Override
    public boolean printReceipt(ReceiptCommand command) {
        log.info("ESC/POS Printer: In hóa đơn số {} cho đơn hàng ID={} tại {}, tổng tiền={}",
                command.receiptNo(), command.orderId(), command.branchName(), command.totalAmount());
        return true;
    }
}

