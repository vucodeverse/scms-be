package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.RecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** Danh mục topping (UC11). */
@Schema(name = "ToppingDto")
public record ToppingDto(
        Integer id,
        String name,
        BigDecimal defaultPrice,
        RecordStatus status) {
}
