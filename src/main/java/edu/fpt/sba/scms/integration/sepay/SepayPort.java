package edu.fpt.sba.scms.integration.sepay;

/**
 * Cổng tích hợp đối soát và xác thực chữ ký Webhook từ cổng thanh toán SePay (UC44, BR-29).
 */
public interface SepayPort {

    /**
     * Xác thực chữ ký/API token trên header Authorization từ Webhook SePay.
     */
    boolean verifySignature(String authHeader, String configuredToken);

    /**
     * Trích xuất mã định danh đơn hàng từ nội dung chuyển khoản.
     */
    Integer extractOrderId(String narration, String prefix);
}

