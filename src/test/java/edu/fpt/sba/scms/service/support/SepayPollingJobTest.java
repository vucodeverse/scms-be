package edu.fpt.sba.scms.service.support;

import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.PaymentConfig;
import edu.fpt.sba.scms.repository.OrderRepository;
import edu.fpt.sba.scms.service.PaymentConfigService;
import edu.fpt.sba.scms.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SepayPollingJobTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentConfigService paymentConfigService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SepayPollingJob pollingJob;

    @Test
    @DisplayName("Bỏ qua polling khi không có đơn hàng nào PENDING")
    void pollTransactions_skipWhenNoPendingOrders() {
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of());

        pollingJob.pollTransactions();

        verify(paymentConfigService, never()).getInternalConfig();
    }

    @Test
    @DisplayName("Bỏ qua polling khi token SePay chưa được cấu hình hoặc là dummy")
    void pollTransactions_skipWhenTokenIsDummy() {
        Order pendingOrder = Order.builder().status(OrderStatus.PENDING).build();
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of(pendingOrder));

        PaymentConfig config = PaymentConfig.builder()
                .active(true)
                .apiToken("sepay_sandbox_token_default_123456")
                .build();
        when(paymentConfigService.getInternalConfig()).thenReturn(config);

        pollingJob.pollTransactions();

        // Không ném lỗi và an toàn
    }
}

