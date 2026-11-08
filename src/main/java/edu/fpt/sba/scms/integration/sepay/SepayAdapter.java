package edu.fpt.sba.scms.integration.sepay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter hiện thực cổng xác thực và trích xuất dữ liệu từ SePay Webhook (UC44, BR-29).
 */
@Slf4j
@Component
public class SepayAdapter implements SepayPort {

    @Override
    public boolean verifySignature(String authHeader, String configuredToken) {
        if (configuredToken == null || configuredToken.isBlank()) {
            // Chưa cấu hình token bí mật -> chấp nhận trong môi trường sandbox/test
            return true;
        }
        if (authHeader == null || authHeader.isBlank()) {
            return false;
        }
        // Hỗ trợ cả định dạng "Apikey {token}", "Bearer {token}" hoặc "{token}"
        String cleaned = authHeader.replace("Apikey ", "").replace("Bearer ", "").trim();
        return configuredToken.trim().equals(cleaned);
    }

    @Override
    public Integer extractOrderId(String narration, String prefix) {
        if (narration == null || narration.isBlank()) {
            return null;
        }

        // 1. Ưu tiên bóc tách theo tiền tố cấu hình
        if (prefix != null && !prefix.isBlank()) {
            Pattern pattern = Pattern.compile(Pattern.quote(prefix.trim()) + "\\s*([0-9]+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(narration);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // 2. Fallback: tự động nhận diện các tiền tố thông dụng (CO, CAFE, ORD, DH)
        Pattern fallbackPattern = Pattern.compile("(?:CO|CAFE|ORD|DH)\\s*([0-9]+)", Pattern.CASE_INSENSITIVE);
        Matcher fallbackMatcher = fallbackPattern.matcher(narration);
        if (fallbackMatcher.find()) {
            try {
                return Integer.parseInt(fallbackMatcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }

        return null;
    }
}

