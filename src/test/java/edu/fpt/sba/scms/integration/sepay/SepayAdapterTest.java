package edu.fpt.sba.scms.integration.sepay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SepayAdapterTest {

    private final SepayAdapter adapter = new SepayAdapter();

    @Test
    @DisplayName("Trích xuất orderId với tiền tố cấu hình CAFE")
    void extractOrderId_withConfiguredPrefix() {
        Integer orderId = adapter.extractOrderId("Thanh toan don hang CAFE1024 tai quay", "CAFE");
        assertThat(orderId).isEqualTo(1024);
    }

    @Test
    @DisplayName("Trích xuất orderId với tiền tố CO từ cấu hình mã thanh toán SePay")
    void extractOrderId_withSepayCOPrefix() {
        // Khớp cấu hình tiền tố CO trên ảnh SePay: CO11111111 hoặc CO5
        Integer orderId1 = adapter.extractOrderId("CO1025 chuyen khoan", "CO");
        assertThat(orderId1).isEqualTo(1025);

        // Fallback tự động nhận diện tiền tố CO dù prefix cấu hình khác
        Integer orderId2 = adapter.extractOrderId("Thanh toan CO9999", "CAFE");
        assertThat(orderId2).isEqualTo(9999);
    }

    @Test
    @DisplayName("Xác thực chữ ký SePay với các định dạng Apikey hoặc Bearer")
    void verifySignature_success() {
        String secret = "my_sepay_secret_token";

        assertThat(adapter.verifySignature("Apikey my_sepay_secret_token", secret)).isTrue();
        assertThat(adapter.verifySignature("Bearer my_sepay_secret_token", secret)).isTrue();
        assertThat(adapter.verifySignature("my_sepay_secret_token", secret)).isTrue();
        assertThat(adapter.verifySignature("Apikey wrong_token", secret)).isFalse();
    }
}

