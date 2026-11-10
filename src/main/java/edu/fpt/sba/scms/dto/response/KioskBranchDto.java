package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "KioskBranchDto", description = "Thông tin tối giản chi nhánh cho màn hình mở khóa PIN / Kiosk")
public record KioskBranchDto(
        Integer id,
        String code,
        String name,
        BranchStatus status
) {
}
