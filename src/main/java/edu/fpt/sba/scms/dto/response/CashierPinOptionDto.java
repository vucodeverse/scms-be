package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "CashierPinOptionDto", description = "Thông tin thu ngân của chi nhánh cho màn hình mở khóa PIN")
public record CashierPinOptionDto(
        Integer employeeId,
        String fullName,
        Integer branchId,
        boolean hasPin,
        boolean pinLocked,
        Instant pinLockedUntil
) {
}
