package edu.fpt.sba.scms.mapper;

import edu.fpt.sba.scms.dto.response.PayPeriodResponse;
import edu.fpt.sba.scms.dto.response.PayrollItemResponse;
import edu.fpt.sba.scms.model.PayPeriod;
import edu.fpt.sba.scms.model.PayrollItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PayrollMapper {

    @Mapping(target = "id", source = "period.id")
    @Mapping(target = "totalEmployees", source = "totalEmployees")
    PayPeriodResponse toPayPeriodResponse(PayPeriod period, int totalEmployees);

    @Mapping(target = "id", source = "item.id")
    @Mapping(target = "fullName", source = "fullName")
    @Mapping(target = "role", source = "role")
    PayrollItemResponse toPayrollItemResponse(PayrollItem item, String fullName, String role);
}

