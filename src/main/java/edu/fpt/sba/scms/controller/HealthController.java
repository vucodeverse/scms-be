package edu.fpt.sba.scms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint "hello" qua envelope (exit criteria P0: GET /health xanh).
 * Đi qua ApiResponseAdvice -> {"success":true,"data":{"service":"scms-be"}...}.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "00. Health", description = "Kiểm tra service đang sống (P0)")
public class HealthController {

    @Operation(summary = "Health check qua envelope")
    @GetMapping
    public Map<String, String> health() {
        return Map.of("service", "scms-be", "status", "UP");
    }
}
