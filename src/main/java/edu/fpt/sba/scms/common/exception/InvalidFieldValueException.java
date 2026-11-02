package edu.fpt.sba.scms.common.exception;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.util.FieldError;
import lombok.Getter;

import java.util.List;

/**
 * 400 VALIDATION_ERROR cho rule kiểm ở tầng service nhưng về bản chất là đầu vào sai
 * (vd. {@code sortBy} ngoài whitelist, mã không được đổi khi cập nhật).
 * <p>
 * Khác {@link BusinessRuleException} (422): lỗi ở đây client tự sửa được bằng cách gửi lại dữ liệu đúng,
 * khác với dữ liệu hợp lệ nhưng bị trạng thái nghiệp vụ chặn.
 */
@Getter
public class InvalidFieldValueException extends BaseException {

    private final String field;

    public InvalidFieldValueException(String field, String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
        this.field = field;
    }

    @Override
    public List<FieldError> fieldErrors() {
        return List.of(new FieldError(field, getMessage()));
    }
}
