package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.ShiftHandoverStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "ShiftHandoverResponse", description = "Chi tiết biên bản bàn giao két giữa ca")
public record ShiftHandoverResponse(
        Integer id,
        Integer shiftId,
        Integer branchId,
        Integer fromUserId,
        String fromUserName,
        Integer toUserId,
        String toUserName,
        ShiftHandoverStatus status,
        String reason,
        BigDecimal expectedCashAtOut,
        BigDecimal outgoingCountedCash,
        BigDecimal outgoingVariance,
        String outgoingNote,
        Instant outgoingAt,
        Integer outgoingConfirmedBy,
        String outgoingConfirmedByName,
        BigDecimal incomingCountedCash,
        BigDecimal incomingVarianceFromOut,
        BigDecimal incomingVarianceFromBook,
        String incomingNote,
        Instant incomingAt,
        Integer incomingConfirmedBy,
        String incomingConfirmedByName,
        Instant cancelledAt,
        Integer cancelledBy,
        String cancelledByName,
        String cancelReason,
        Instant createdAt
) {
}
