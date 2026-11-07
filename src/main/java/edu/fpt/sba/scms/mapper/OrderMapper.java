package edu.fpt.sba.scms.mapper;

import edu.fpt.sba.scms.dto.response.OrderDetailDto;
import edu.fpt.sba.scms.dto.response.OrderDto;
import edu.fpt.sba.scms.dto.response.OrderItemToppingDto;
import edu.fpt.sba.scms.dto.response.OrderLineDto;
import edu.fpt.sba.scms.dto.response.OrderVoucherDto;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.OrderItemTopping;
import edu.fpt.sba.scms.model.OrderLine;
import edu.fpt.sba.scms.model.OrderVoucher;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper chuyển đổi Entity ↔ DTO cho phân hệ Đơn hàng (Order).
 */
@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderDto toDto(Order order);

    @Mapping(target = "id", source = "order.id")
    @Mapping(target = "branchId", source = "order.branchId")
    @Mapping(target = "shiftId", source = "order.shiftId")
    @Mapping(target = "cashierId", source = "order.cashierId")
    @Mapping(target = "status", source = "order.status")
    @Mapping(target = "totalAmount", source = "order.totalAmount")
    @Mapping(target = "discountAmount", source = "order.discountAmount")
    @Mapping(target = "finalAmount", source = "order.finalAmount")
    @Mapping(target = "note", source = "order.note")
    @Mapping(target = "receiptNo", source = "order.receiptNo")
    @Mapping(target = "receiptPrintedAt", source = "order.receiptPrintedAt")
    @Mapping(target = "cancelReason", source = "order.cancelReason")
    @Mapping(target = "createdAt", source = "order.createdAt")
    @Mapping(target = "paidAt", source = "order.paidAt")
    @Mapping(target = "cancelledAt", source = "order.cancelledAt")
    @Mapping(target = "lines", source = "lines")
    @Mapping(target = "voucher", source = "voucher")
    OrderDetailDto toDetailDto(Order order, List<OrderLineDto> lines, OrderVoucherDto voucher);

    @Mapping(target = "id", source = "line.id")
    @Mapping(target = "productId", source = "line.productId")
    @Mapping(target = "productName", source = "productName")
    @Mapping(target = "size", source = "line.size")
    @Mapping(target = "quantity", source = "line.quantity")
    @Mapping(target = "unitPrice", source = "line.unitPrice")
    @Mapping(target = "toppingTotal", source = "line.toppingTotal")
    @Mapping(target = "sugarLevel", source = "line.sugarLevel")
    @Mapping(target = "iceLevel", source = "line.iceLevel")
    @Mapping(target = "note", source = "line.note")
    @Mapping(target = "lineTotal", source = "line.lineTotal")
    @Mapping(target = "cogsAmount", source = "line.cogsAmount")
    @Mapping(target = "toppings", source = "toppings")
    OrderLineDto toLineDto(OrderLine line, String productName, List<OrderItemToppingDto> toppings);

    @Mapping(target = "id", source = "itemTopping.id")
    @Mapping(target = "toppingId", source = "itemTopping.toppingId")
    @Mapping(target = "toppingName", source = "toppingName")
    @Mapping(target = "extraPrice", source = "itemTopping.extraPrice")
    OrderItemToppingDto toToppingDto(OrderItemTopping itemTopping, String toppingName);

    @Mapping(target = "id", source = "orderVoucher.id")
    @Mapping(target = "voucherId", source = "orderVoucher.voucherId")
    @Mapping(target = "voucherCode", source = "voucherCode")
    @Mapping(target = "discountAmountApplied", source = "orderVoucher.discountAmountApplied")
    OrderVoucherDto toVoucherDto(OrderVoucher orderVoucher, String voucherCode);
}
