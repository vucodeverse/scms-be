package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.PaymentMethod;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.common.exception.ReportGenerationException;
import edu.fpt.sba.scms.common.util.VietnameseNumberToWords;
import edu.fpt.sba.scms.dto.response.ReceiptResponse;
import edu.fpt.sba.scms.dto.response.report.ReportFileDto;
import edu.fpt.sba.scms.integration.export.ReceiptPdfWriter;
import edu.fpt.sba.scms.integration.export.ReceiptPrintModel;
import edu.fpt.sba.scms.integration.printer.PrinterPort;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.OrderItemTopping;
import edu.fpt.sba.scms.model.OrderLine;
import edu.fpt.sba.scms.model.Payment;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.Topping;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.OrderItemToppingRepository;
import edu.fpt.sba.scms.repository.OrderLineRepository;
import edu.fpt.sba.scms.repository.OrderRepository;
import edu.fpt.sba.scms.repository.PaymentRepository;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.repository.ToppingRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.security.BranchContext;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderItemToppingRepository orderItemToppingRepository;
    private final ProductRepository productRepository;
    private final ToppingRepository toppingRepository;
    private final BranchRepository branchRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ReceiptPdfWriter receiptPdfWriter;
    private final PrinterPort printerPort;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public ReceiptResponse getReceipt(Integer orderId, UserPrincipal principal) {
        Order order = findOrderAndValidate(orderId);
        return buildReceiptResponse(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportFileDto exportReceiptPdf(Integer orderId, UserPrincipal principal) {
        Order order = findOrderAndValidate(orderId);
        ReceiptResponse response = buildReceiptResponse(order);
        ReceiptPrintModel printModel = toPrintModel(response);

        byte[] pdfBytes;
        try {
            pdfBytes = receiptPdfWriter.write(printModel);
        } catch (IOException e) {
            log.error("Lỗi khi kết xuất file PDF biên lai cho đơn hàng ID={}", orderId, e);
            throw new ReportGenerationException(e);
        }

        Instant now = Instant.now();
        order.setReceiptPrintedAt(now);
        orderRepository.save(order);

        auditLogService.log(AuditAction.RECEIPT_PRINT, "order", order.getId(), null,
                "Xuất file PDF biên lai cho đơn hàng #" + order.getId());

        String fileName = "hoa-don-#" + order.getId() + ".pdf";
        return new ReportFileDto(fileName, "application/pdf", pdfBytes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceiptResponse printReceipt(Integer orderId, UserPrincipal principal) {
        Order order = findOrderAndValidate(orderId);
        ReceiptResponse response = buildReceiptResponse(order);

        Instant now = Instant.now();
        printerPort.printReceipt(new PrinterPort.ReceiptCommand(
                order.getId(),
                order.getReceiptNo(),
                response.branchName(),
                response.cashierName(),
                response.finalAmount(),
                now
        ));

        order.setReceiptPrintedAt(now);
        orderRepository.save(order);

        auditLogService.log(AuditAction.RECEIPT_PRINT, "order", order.getId(), null,
                "Gửi lệnh in biên lai cho đơn hàng #" + order.getId() + " qua máy in nhiệt ESC/POS");

        return buildReceiptResponse(order);
    }

    private Order findOrderAndValidate(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("đơn hàng"));

        // Cô lập chi nhánh
        BranchContext.resolveBranchScope(order.getBranchId());

        // Quy tắc BR-31: Hóa đơn chỉ được in/xuất sau khi đơn hàng đã chuyển trạng thái PAID
        if (!order.isPaid()) {
            throw new BusinessRuleException(BusinessErrorCode.ORDER_NOT_PAID);
        }

        return order;
    }

    private ReceiptResponse buildReceiptResponse(Order order) {
        Branch branch = branchRepository.findById(order.getBranchId()).orElse(null);
        String branchName = branch != null ? branch.getName() : "SMART CAFE";
        String branchAddress = branch != null ? branch.getAddress() : "";
        String branchPhone = branch != null ? branch.getPhone() : "";

        User cashier = order.getCashierId() != null
                ? userRepository.findById(order.getCashierId()).orElse(null)
                : null;
        String cashierName = cashier != null ? cashier.getFullName() : "Thu ngân";

        Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId()).orElse(null);
        boolean isCash = payment == null || payment.getMethod() == null || payment.getMethod() == PaymentMethod.CASH;
        String paymentMethodStr = (payment != null && payment.getMethod() != null)
                ? (payment.getMethod() == PaymentMethod.VIETQR ? "VIETQR" : "TIỀN MẶT")
                : "TIỀN MẶT";

        BigDecimal receivedAmount = null;
        BigDecimal changeReturned = null;
        if (isCash) {
            receivedAmount = payment != null && payment.getReceivedAmount() != null
                    ? payment.getReceivedAmount()
                    : order.getFinalAmount();
            changeReturned = receivedAmount.subtract(order.getFinalAmount()).max(BigDecimal.ZERO);
        }

        List<OrderLine> lines = orderLineRepository.findByOrderId(order.getId());
        List<ReceiptResponse.ReceiptItemDto> itemDtos = new ArrayList<>(lines.size());
        int totalQuantity = 0;
        int idx = 1;

        for (OrderLine line : lines) {
            String prodName = productRepository.findById(line.getProductId())
                    .map(Product::getName)
                    .orElse("Sản phẩm #" + line.getProductId());

            List<OrderItemTopping> toppings = orderItemToppingRepository.findByOrderLineId(line.getId());
            List<ReceiptResponse.ReceiptToppingDto> toppingDtos = toppings.stream().map(t -> {
                String topName = toppingRepository.findById(t.getToppingId())
                        .map(Topping::getName)
                        .orElse("Topping #" + t.getToppingId());
                return new ReceiptResponse.ReceiptToppingDto(topName, 1, t.getExtraPrice());
            }).toList();

            List<String> optionsList = new ArrayList<>();
            if (line.getSugarLevel() != null && !line.getSugarLevel().isBlank()) {
                optionsList.add(line.getSugarLevel() + "% Đường");
            }
            if (line.getIceLevel() != null && !line.getIceLevel().isBlank()) {
                optionsList.add(line.getIceLevel() + "% Đá");
            }
            String options = String.join(", ", optionsList);

            totalQuantity += line.getQuantity();
            itemDtos.add(new ReceiptResponse.ReceiptItemDto(
                    idx++,
                    prodName,
                    line.getSize(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.getLineTotal(),
                    toppingDtos,
                    options
            ));
        }

        boolean hasDiscount = order.getDiscountAmount() != null && order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0;
        String amountInWords = VietnameseNumberToWords.convert(order.getFinalAmount());
        Instant printTime = order.getReceiptPrintedAt() != null
                ? order.getReceiptPrintedAt()
                : (order.getPaidAt() != null ? order.getPaidAt() : Instant.now());

        return new ReceiptResponse(
                order.getId(),
                order.getReceiptNo(),
                printTime,
                branchName,
                branchAddress,
                branchPhone,
                cashierName,
                itemDtos,
                totalQuantity,
                order.getTotalAmount(),
                order.getDiscountAmount(),
                hasDiscount,
                order.getFinalAmount(),
                paymentMethodStr,
                receivedAmount,
                changeReturned,
                isCash,
                amountInWords
        );
    }

    private static ReceiptPrintModel toPrintModel(ReceiptResponse r) {
        Instant printTime = r.printedAt() != null ? r.printedAt() : Instant.now();
        String dateStr = DATE_FMT.format(printTime);
        String timeStr = TIME_FMT.format(printTime);

        List<ReceiptPrintModel.Item> printItems = r.items().stream().map(it -> {
            List<String> topStrs = it.toppings().stream()
                    .map(t -> t.name() + " (" + (t.price() != null ? t.price().toPlainString() : "0") + ")")
                    .toList();
            return new ReceiptPrintModel.Item(
                    it.index(),
                    it.productName(),
                    it.size(),
                    it.quantity(),
                    it.unitPrice(),
                    it.lineTotal(),
                    topStrs,
                    it.options()
            );
        }).toList();

        return new ReceiptPrintModel(
                r.orderId(),
                r.receiptNo(),
                dateStr,
                timeStr,
                r.branchName(),
                r.branchAddress(),
                r.branchPhone(),
                r.cashierName(),
                printItems,
                r.totalQuantity(),
                r.totalAmount(),
                r.discountAmount(),
                r.hasDiscount(),
                r.finalAmount(),
                r.paymentMethod(),
                r.receivedAmount(),
                r.changeReturned(),
                r.isCash(),
                r.amountInWords()
        );
    }
}
