package edu.fpt.sba.scms.mapper;

import edu.fpt.sba.scms.dto.response.RefundResponse;
import edu.fpt.sba.scms.model.Refund;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RefundMapper {

    @Mapping(target = "id", source = "refund.id")
    @Mapping(target = "message", source = "message")
    RefundResponse toRefundResponse(Refund refund, String message);
}

