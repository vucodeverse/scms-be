package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(name = "ReceiptResponse", description = "Dữ liệu biên lai thanh toán chi tiết cho đơn hàng tại quầy (UC22, BR-31)")
public record ReceiptResponse(
        @Schema(description = "Mã đơn hàng", example = "105")
        Integer orderId,

        @Schema(description = "Số hóa đơn / mã biên lai", example = "ORD-1712800000000")
        String receiptNo,

        @Schema(description = "Thời gian in biên lai")
        Instant printedAt,

        @Schema(description = "Tên chi nhánh", example = "Chi nhánh Quận 10 - Sư Vạn Hạnh")
        String branchName,

        @Schema(description = "Địa chỉ chi nhánh", example = "355 Sư Vạn Hạnh, P.12, Q.10")
        String branchAddress,

        @Schema(description = "Số điện thoại chi nhánh", example = "090.126.9955")
        String branchPhone,

        @Schema(description = "Họ tên thu ngân thực hiện", example = "Nguyễn Văn A")
        String cashierName,

        @Schema(description = "Danh sách chi tiết các món trong đơn hàng")
        List<ReceiptItemDto> items,

        @Schema(description = "Tổng số lượng ly/món", example = "4")
        int totalQuantity,

        @Schema(description = "Tổng tiền hàng gốc trước khi áp voucher", example = "150000")
        BigDecimal totalAmount,

        @Schema(description = "Số tiền giảm giá qua voucher (nếu có)", example = "20000")
        BigDecimal discountAmount,

        @Schema(description = "Có áp dụng giảm giá hay không", example = "true")
        boolean hasDiscount,

        @Schema(description = "Tổng số tiền thực tế phải thanh toán", example = "130000")
        BigDecimal finalAmount,

        @Schema(description = "Hình thức thanh toán (TIỀN MẶT / VIETQR)", example = "TIỀN MẶT")
        String paymentMethod,

        @Schema(description = "Số tiền khách đưa (chỉ có khi thanh toán tiền mặt)", example = "200000")
        BigDecimal receivedAmount,

        @Schema(description = "Số tiền thối lại cho khách (chỉ có khi thanh toán tiền mặt)", example = "70000")
        BigDecimal changeReturned,

        @Schema(description = "Có phải thanh toán tiền mặt không", example = "true")
        boolean isCash,

        @Schema(description = "Số tiền thanh toán bằng chữ tiếng Việt", example = "Một trăm ba mươi ngàn đồng./")
        String amountInWords
) {
    public record ReceiptItemDto(
            @Schema(description = "Số thứ tự dòng món", example = "1")
            int index,

            @Schema(description = "Tên sản phẩm", example = "Cà Phê Sữa")
            String productName,

            @Schema(description = "Size sản phẩm (nếu có)", example = "L")
            String size,

            @Schema(description = "Số lượng", example = "2")
            int quantity,

            @Schema(description = "Đơn giá món cơ bản", example = "35000")
            BigDecimal unitPrice,

            @Schema(description = "Tổng tiền của dòng món (đã bao gồm topping)", example = "70000")
            BigDecimal lineTotal,

            @Schema(description = "Danh sách topping chọn thêm cho món")
            List<ReceiptToppingDto> toppings,

            @Schema(description = "Tùy chọn đường/đá (nếu có)", example = "50% Đường, 70% Đá")
            String options
    ) {
    }

    public record ReceiptToppingDto(
            @Schema(description = "Tên topping", example = "Trân châu trắng")
            String name,

            @Schema(description = "Số lượng topping", example = "1")
            int quantity,

            @Schema(description = "Đơn giá thêm của topping", example = "5000")
            BigDecimal price
    ) {
    }
}
