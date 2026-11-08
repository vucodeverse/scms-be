package edu.fpt.sba.scms.mapper;

import edu.fpt.sba.scms.dto.response.PaymentConfigResponse;
import edu.fpt.sba.scms.model.PaymentConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper chuyển đổi Entity ↔ DTO cho cấu hình thanh toán SePay (UC15).
 */
@Mapper(componentModel = "spring")
public interface PaymentConfigMapper {

    @Mapping(target = "id", source = "config.id")
    @Mapping(target = "bankName", source = "config.bankName")
    @Mapping(target = "accountNumber", source = "config.accountNumber")
    @Mapping(target = "accountHolder", source = "config.accountHolder")
    @Mapping(target = "paymentPrefix", source = "config.paymentPrefix")
    @Mapping(target = "maskedApiToken", source = "maskedApiToken")
    @Mapping(target = "webhookUrl", source = "config.webhookUrl")
    @Mapping(target = "active", source = "config.active")
    @Mapping(target = "updatedAt", source = "config.updatedAt")
    PaymentConfigResponse toDto(PaymentConfig config, String maskedApiToken);
}

