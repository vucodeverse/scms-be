package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "OrderItemToppingDto", description = "Topping kèm theo dòng món")
public record OrderItemToppingDto(
        Integer id,
        Integer toppingId,
        String toppingName,
        BigDecimal extraPrice
) {
}
