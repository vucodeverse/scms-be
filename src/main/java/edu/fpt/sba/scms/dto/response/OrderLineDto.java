package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(name = "OrderLineDto", description = "Chi tiết dòng món trong đơn hàng (BR-25)")
public record OrderLineDto(
        Integer id,
        Integer productId,
        String productName,
        String size,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal toppingTotal,
        String sugarLevel,
        String iceLevel,
        String note,
        BigDecimal lineTotal,
        BigDecimal cogsAmount,
        List<OrderItemToppingDto> toppings
) {
}
