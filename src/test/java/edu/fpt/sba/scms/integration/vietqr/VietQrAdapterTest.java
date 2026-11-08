package edu.fpt.sba.scms.integration.vietqr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class VietQrAdapterTest {

    private final VietQrAdapter adapter = new VietQrAdapter();

    @Test
    @DisplayName("Tạo VietQR thành công với VPBank và tài khoản TRAN VIET ANH")
    void generateQr_vpbank_success() {
        VietQrPort.VietQrCommand command = new VietQrPort.VietQrCommand(
                "Vpbank",
                "999907122004",
                "TRAN VIET ANH",
                new BigDecimal("50000"),
                "CAFE101"
        );

        VietQrPort.VietQrData data = adapter.generateQr(command);

        assertThat(data).isNotNull();
        assertThat(data.qrImageUrl())
                .isEqualTo("https://img.vietqr.io/image/VPBank-999907122004-compact2.png?amount=50000&addInfo=CAFE101&accountName=TRAN+VIET+ANH");
        assertThat(data.qrPayload())
                .isEqualTo("vietqr://transfer?bank=VPBank&acc=999907122004&amount=50000&memo=CAFE101");
    }

    @Test
    @DisplayName("Tạo VietQR chuẩn hóa bank code khi null hoặc rỗng mặc định về VPBank")
    void generateQr_defaultBankToVpbank() {
        VietQrPort.VietQrCommand command = new VietQrPort.VietQrCommand(
                null,
                "999907122004",
                "TRAN VIET ANH",
                new BigDecimal("75000"),
                "CAFE102"
        );

        VietQrPort.VietQrData data = adapter.generateQr(command);

        assertThat(data.qrImageUrl()).contains("VPBank-999907122004");
        assertThat(data.qrPayload()).contains("bank=VPBank");
    }
}

