package edu.fpt.sba.scms.common.exception;

import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.ErrorCode;

/**
 * 422 BUSINESS_RULE_VIOLATION. Mã con (VOUCHER_EXPIRED, ORDER_FROZEN...) nằm ở
 * {@link #detailCode}; FE hiển thị {@link #getMessage()}.
 */
public class BusinessRuleException extends BaseException {

    private final String detailCode;

    public BusinessRuleException(BusinessErrorCode detailCode) {
        this(detailCode, detailCode.message());
    }

    public BusinessRuleException(BusinessErrorCode detailCode, String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
        this.detailCode = detailCode.name();
    }

    public String getDetailCode() {
        return detailCode;
    }
}
