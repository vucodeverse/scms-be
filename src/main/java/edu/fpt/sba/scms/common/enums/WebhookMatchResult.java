package edu.fpt.sba.scms.common.enums;

/**
 * Kết quả đối soát Webhook SePay (SDD 5.1.32).
 */
public enum WebhookMatchResult {
    MATCH,
    MISMATCH,
    DUPLICATE,
    INVALID_SIG,
    NOT_FOUND
}

