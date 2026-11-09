package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "PinLockActionRequest", description = "Ghi chú tùy chọn khi xác nhận cảnh báo hoặc mở khóa PIN sớm")
public record PinLockActionRequest(
        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String note
) {
}
