package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "SyncConfigResponse", description = "Kết quả đồng bộ cấu hình chuỗi xuống chi nhánh")
public record SyncConfigResponse(
        boolean success,
        List<Integer> syncedBranchIds,
        Instant syncedAt,
        String message
) {
}

