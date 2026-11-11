package edu.fpt.sba.scms.integration.export;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mô hình dữ liệu trung lập phục vụ xuất/in biên lai thanh toán (UC22).
 */
public record ReceiptPrintModel(
        Integer orderId,
        String receiptNo,
        String printedDate,
        String printedTime,
        String branchName,
        String branchAddress,
        String branchPhone,
        String cashierName,
        List<Item> items,
        int totalQuantity,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        boolean hasDiscount,
        BigDecimal finalAmount,
        String paymentMethod,
        BigDecimal receivedAmount,
        BigDecimal changeReturned,
        boolean isCash,
        String amountInWords
) {
    public record Item(
            int index,
            String productName,
            String size,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            List<String> toppings,
            String options
    ) {
    }
}

