package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.DiscountType;
import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.SortDirection;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.domain.order.OrderPricingCalculator;
import edu.fpt.sba.scms.domain.order.OrderStatePolicy;
import edu.fpt.sba.scms.domain.voucher.VoucherDiscountCalculator;
import edu.fpt.sba.scms.domain.voucher.VoucherEligibilityPolicy;
import edu.fpt.sba.scms.dto.request.ApplyVoucherRequest;
import edu.fpt.sba.scms.dto.request.CancelOrderRequest;
import edu.fpt.sba.scms.dto.request.CreateOrderLineRequest;
import edu.fpt.sba.scms.dto.request.CreateOrderRequest;
import edu.fpt.sba.scms.dto.request.query.OrderListQuery;
import edu.fpt.sba.scms.dto.response.OrderDetailDto;
import edu.fpt.sba.scms.dto.response.OrderDto;
import edu.fpt.sba.scms.dto.response.OrderItemToppingDto;
import edu.fpt.sba.scms.dto.response.OrderLineDto;
import edu.fpt.sba.scms.dto.response.OrderVoucherDto;
import edu.fpt.sba.scms.mapper.OrderMapper;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.OrderItemTopping;
import edu.fpt.sba.scms.model.OrderLine;
import edu.fpt.sba.scms.model.OrderVoucher;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.model.Topping;
import edu.fpt.sba.scms.model.Voucher;
import edu.fpt.sba.scms.repository.OrderItemToppingRepository;
import edu.fpt.sba.scms.repository.OrderLineRepository;
import edu.fpt.sba.scms.repository.OrderRepository;
import edu.fpt.sba.scms.repository.OrderSpecs;
import edu.fpt.sba.scms.repository.OrderVoucherRepository;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.ToppingRepository;
import edu.fpt.sba.scms.repository.VoucherRepository;
import edu.fpt.sba.scms.security.BranchContext;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderItemToppingRepository orderItemToppingRepository;
    private final OrderVoucherRepository orderVoucherRepository;
    private final VoucherRepository voucherRepository;
    private final ProductRepository productRepository;
    private final ShiftRepository shiftRepository;
    private final ToppingRepository toppingRepository;
    private final AuditLogService auditLogService;
    private final OrderMapper orderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailDto createOrder(CreateOrderRequest request, UserPrincipal principal) {
        Integer branchId = BranchContext.resolveBranchScope(request.branchId());
        ensureDrawerNotLockedForHandover(branchId != null ? branchId : 1);
        Integer cashierId = principal != null ? principal.userId() : null;

        List<CreateOrderLineRequest> lineRequests = resolveLineRequests(request);
        BigDecimal totalAmount = calculateTotalAmount(lineRequests, request);

        Order savedOrder = saveNewOrder(request, branchId, cashierId, totalAmount);
        List<OrderLineDto> lineDtos = saveAllLinesWithToppings(lineRequests, savedOrder.getId());

        log.info("Tạo đơn hàng POS ID={} tại chi nhánh {}, tổng thanh toán={}",
                savedOrder.getId(), savedOrder.getBranchId(), savedOrder.getFinalAmount());

        return orderMapper.toDetailDto(savedOrder, lineDtos, null);
    }

    private void ensureDrawerNotLockedForHandover(Integer branchId) {
        if (branchId == null) {
            return;
        }
        shiftRepository.findFirstByBranchIdAndStatusOrderByOpenedAtDesc(branchId, ShiftStatus.OPEN)
                .filter(Shift::isHandoverPending)
                .ifPresent(s -> {
                    throw new BusinessRuleException(BusinessErrorCode.SHIFT_HANDOVER_PENDING,
                            "Két tiền đang tạm khóa chờ xác nhận bàn giao giữa ca #" + s.getId()
                                    + ". Vui lòng kiểm két nhận ca trước khi tạo đơn POS mới.");
                });
    }

    /** Trả về danh sách line từ request (rỗng nếu không có). */
    private List<CreateOrderLineRequest> resolveLineRequests(CreateOrderRequest request) {
        if (request.lines() == null || request.lines().isEmpty()) {
            return List.of();
        }
        return request.lines();
    }

    /**
     * Tính tổng tiền hàng: duyệt từng line, gọi {@link OrderPricingCalculator#calculateLineTotal}
     * và cộng dồn — không ghi DB.
     */
    private BigDecimal calculateTotalAmount(List<CreateOrderLineRequest> lineRequests,
                                            CreateOrderRequest request) {
        if (lineRequests.isEmpty()) {
            return request.totalAmount() != null ? request.totalAmount() : BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderLineRequest lineReq : lineRequests) {
            BigDecimal toppingTotal = fetchToppingTotal(lineReq.toppingIds());
            total = total.add(OrderPricingCalculator.calculateLineTotal(lineReq.unitPrice(), toppingTotal, lineReq.quantity()));
        }
        return total;
    }

    /** Tính tổng phụ phí topping từ danh sách id — batch fetch, không ghi DB. */
    private BigDecimal fetchToppingTotal(List<Integer> toppingIds) {
        if (toppingIds == null || toppingIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<Topping> toppings = toppingRepository.findAllByIdInAndStatus(toppingIds, RecordStatus.ACTIVE);
        return toppings.stream()
                .map(Topping::getDefaultPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Khởi tạo và lưu entity Order mới. */
    private Order saveNewOrder(CreateOrderRequest request, Integer branchId,
                               Integer cashierId, BigDecimal totalAmount) {
        BigDecimal discountAmount = request.discountAmount() != null
                ? request.discountAmount() : BigDecimal.ZERO;

        Order order = Order.builder()
                .branchId(branchId != null ? branchId : 1)
                .shiftId(request.shiftId())
                .cashierId(cashierId)
                .status(OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .discountAmount(discountAmount)
                .finalAmount(OrderPricingCalculator.calculateFinalAmount(totalAmount, discountAmount))
                .note(request.note())
                .receiptNo("ORD-" + System.currentTimeMillis())
                .build();

        return orderRepository.save(order);
    }

    /**
     * Lưu toàn bộ order-line và topping của chúng.
     * Mỗi line được xử lý bởi {@link #saveLineWithToppings}.
     */
    private List<OrderLineDto> saveAllLinesWithToppings(List<CreateOrderLineRequest> lineRequests,
                                                        Integer orderId) {
        List<OrderLineDto> result = new ArrayList<>();
        for (CreateOrderLineRequest lineReq : lineRequests) {
            result.add(saveLineWithToppings(lineReq, orderId));
        }
        return result;
    }

    /**
     * Validate sản phẩm, lưu một OrderLine và tất cả topping của nó,
     * rồi trả về DTO.
     */
    private OrderLineDto saveLineWithToppings(CreateOrderLineRequest lineReq, Integer orderId) {
        Product product = requireAvailableProduct(lineReq.productId());

        List<Topping> toppings = fetchActiveToppings(lineReq.toppingIds());
        BigDecimal toppingTotal = sumToppingPrices(toppings);
        BigDecimal lineTotal = OrderPricingCalculator.calculateLineTotal(lineReq.unitPrice(), toppingTotal, lineReq.quantity());

        OrderLine savedLine = saveOrderLine(lineReq, product.getId(), orderId, toppingTotal, lineTotal);
        List<OrderItemToppingDto> toppingDtos = saveToppingsForLine(toppings, savedLine.getId());

        return orderMapper.toLineDto(savedLine, product.getName(), toppingDtos);
    }

    /** Tìm sản phẩm và kiểm tra trạng thái AVAILABLE — Fail Fast. */
    private Product requireAvailableProduct(Integer productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("sản phẩm"));
        if (product.getStatus() != ProductStatus.AVAILABLE) {
            throw new BusinessRuleException(BusinessErrorCode.PRODUCT_UNAVAILABLE,
                    "Sản phẩm '" + product.getName() + "' hiện không khả dụng để đặt hàng.");
        }
        return product;
    }

    /** Batch-fetch topping ACTIVE theo danh sách id. */
    private List<Topping> fetchActiveToppings(List<Integer> toppingIds) {
        if (toppingIds == null || toppingIds.isEmpty()) {
            return List.of();
        }
        return toppingRepository.findAllByIdInAndStatus(toppingIds, RecordStatus.ACTIVE);
    }

    /** Cộng tổng giá topping từ danh sách entity. */
    private BigDecimal sumToppingPrices(List<Topping> toppings) {
        return toppings.stream()
                .map(Topping::getDefaultPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Khởi tạo và lưu entity OrderLine. */
    private OrderLine saveOrderLine(CreateOrderLineRequest lineReq, Integer productId,
                                    Integer orderId, BigDecimal toppingTotal, BigDecimal lineTotal) {
        OrderLine line = OrderLine.builder()
                .orderId(orderId)
                .productId(productId)
                .quantity(lineReq.quantity())
                .size(lineReq.size())
                .unitPrice(lineReq.unitPrice())
                .toppingTotal(toppingTotal)
                .sugarLevel(lineReq.sugarLevel())
                .iceLevel(lineReq.iceLevel())
                .note(lineReq.note())
                .lineTotal(lineTotal)
                .build();
        return orderLineRepository.save(line);
    }

    /** Lưu tất cả topping cho một order-line và trả về danh sách DTO. */
    private List<OrderItemToppingDto> saveToppingsForLine(List<Topping> toppings, Integer orderLineId) {
        List<OrderItemToppingDto> dtos = new ArrayList<>();
        for (Topping topping : toppings) {
            OrderItemTopping saved = orderItemToppingRepository.save(
                    OrderItemTopping.builder()
                            .orderLineId(orderLineId)
                            .toppingId(topping.getId())
                            .extraPrice(topping.getDefaultPrice())
                            .build());
            dtos.add(orderMapper.toToppingDto(saved, topping.getName()));
        }
        return dtos;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderDto> listOrders(OrderListQuery query) {
        Integer branchId = BranchContext.resolveBranchScope(query.branchId());

        Sort.Direction direction = query.sortDir() == SortDirection.ASC
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        String sortBy = query.sortBy() != null && !query.sortBy().isBlank()
                ? query.sortBy()
                : "createdAt";

        int pageIndex = Math.max(0, (query.page() != null ? query.page() : 1) - 1);
        int pageSize = query.size() != null ? query.size() : 10;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(direction, sortBy));

        Page<Order> page = orderRepository.findAll(
                OrderSpecs.filter(branchId, query.status(), query.fromDate(), query.toDate(), query.keyword()),
                pageable);

        return page.map(orderMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailDto getOrderById(Integer id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("đơn hàng"));

        BranchContext.resolveBranchScope(order.getBranchId());

        return buildOrderDetailDto(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailDto applyVoucher(Integer id, ApplyVoucherRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("đơn hàng"));

        BranchContext.resolveBranchScope(order.getBranchId());

        if (OrderStatePolicy.isFrozen(order.getStatus())) {
            throw new BusinessRuleException(BusinessErrorCode.ORDER_FROZEN,
                    "Không thể áp dụng voucher cho đơn hàng đã thanh toán hoặc đã hủy.");
        }

        if (!OrderStatePolicy.canApplyVoucher(order.getStatus(), orderVoucherRepository.existsByOrderId(order.getId()))) {
            throw new BusinessRuleException(BusinessErrorCode.VOUCHER_ALREADY_APPLIED,
                    "Đơn hàng đã được áp dụng một voucher khác trước đó.");
        }

        Voucher voucher = voucherRepository.findByCode(request.code().trim().toUpperCase())
                .orElseThrow(() -> new NotFoundException("voucher"));

        if (!VoucherEligibilityPolicy.isActive(voucher.getStatus())) {
            throw new BusinessRuleException(BusinessErrorCode.VOUCHER_EXPIRED,
                    "Voucher hiện không còn hoạt động.");
        }

        Instant now = Instant.now();
        if (!VoucherEligibilityPolicy.isWithinPeriod(now, voucher.getStartDate(), voucher.getEndDate())) {
            throw new BusinessRuleException(BusinessErrorCode.VOUCHER_EXPIRED,
                    "Voucher đã hết hạn hoặc chưa đến ngày bắt đầu.");
        }

        if (VoucherEligibilityPolicy.isUsageLimitReached(voucher.getUsageLimit(), voucher.getUsedCount())) {
            throw new BusinessRuleException(BusinessErrorCode.VOUCHER_USAGE_EXCEEDED,
                    "Voucher đã hết lượt sử dụng.");
        }

        if (!VoucherEligibilityPolicy.meetsMinimumOrderAmount(order.getTotalAmount(), voucher.getMinOrderAmount())) {
            throw new BusinessRuleException(BusinessErrorCode.VOUCHER_MIN_NOT_MET,
                    "Đơn hàng chưa đạt giá trị tối thiểu " + voucher.getMinOrderAmount() + " để áp dụng voucher.");
        }

        BigDecimal discount = VoucherDiscountCalculator.calculateDiscount(
                voucher.getDiscountType(), voucher.getDiscountValue(), order.getTotalAmount());
        BigDecimal finalAmount = OrderPricingCalculator.calculateFinalAmount(order.getTotalAmount(), discount);

        OrderVoucher orderVoucher = OrderVoucher.builder()
                .orderId(order.getId())
                .voucherId(voucher.getId())
                .discountAmountApplied(discount)
                .build();
        orderVoucherRepository.save(orderVoucher);

        voucher.setUsedCount(voucher.getUsedCount() + 1);
        voucherRepository.save(voucher);

        order.setDiscountAmount(discount);
        order.setFinalAmount(finalAmount);
        Order updatedOrder = orderRepository.save(order);

        log.info("Áp dụng voucher {} cho đơn hàng ID={}, giảm={}, thanh toán={}",
                voucher.getCode(), order.getId(), discount, finalAmount);

        return buildOrderDetailDto(updatedOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailDto removeVoucher(Integer id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("đơn hàng"));

        BranchContext.resolveBranchScope(order.getBranchId());

        if (!OrderStatePolicy.canRemoveVoucher(order.getStatus())) {
            throw new BusinessRuleException(BusinessErrorCode.ORDER_FROZEN,
                    "Không thể gỡ voucher khỏi đơn hàng đã thanh toán hoặc đã hủy.");
        }

        orderVoucherRepository.findByOrderId(order.getId()).ifPresent(ov -> {
            voucherRepository.findById(ov.getVoucherId()).ifPresent(v -> {
                if (v.getUsedCount() > 0) {
                    v.setUsedCount(v.getUsedCount() - 1);
                    voucherRepository.save(v);
                }
            });
            orderVoucherRepository.delete(ov);
        });

        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFinalAmount(order.getTotalAmount());
        Order updatedOrder = orderRepository.save(order);

        log.info("Đã gỡ voucher khỏi đơn hàng ID={}", order.getId());

        return buildOrderDetailDto(updatedOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDto cancelOrder(Integer id, CancelOrderRequest request, UserPrincipal principal) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("đơn hàng"));

        BranchContext.resolveBranchScope(order.getBranchId());

        if (order.isPaid()) {
            throw new BusinessRuleException(BusinessErrorCode.ORDER_FROZEN,
                    "Đơn hàng đã thanh toán (PAID) không thể hủy trực tiếp, phải qua quy trình hoàn tiền (BR-26).");
        }

        if (order.isCancelled()) {
            throw new BusinessRuleException(BusinessErrorCode.ORDER_FROZEN,
                    "Đơn hàng đã ở trạng thái hủy trước đó.");
        }

        if (!OrderStatePolicy.canCancel(order.getStatus())) {
            throw new BusinessRuleException(BusinessErrorCode.ORDER_FROZEN,
                    "Chỉ có thể hủy đơn hàng ở trạng thái PENDING.");
        }

        if (request.cancelReason() == null || request.cancelReason().isBlank()) {
            throw new BusinessRuleException(BusinessErrorCode.REASON_REQUIRED,
                    "Lý do hủy đơn hàng là bắt buộc (BR-26b).");
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(request.cancelReason().trim());
        order.setCancelledAt(Instant.now());
        Order cancelledOrder = orderRepository.save(order);

        // Gỡ lượt dùng voucher nếu có
        orderVoucherRepository.findByOrderId(order.getId()).ifPresent(ov -> {
            voucherRepository.findById(ov.getVoucherId()).ifPresent(v -> {
                if (v.getUsedCount() > 0) {
                    v.setUsedCount(v.getUsedCount() - 1);
                    voucherRepository.save(v);
                }
            });
            orderVoucherRepository.delete(ov);
        });

        auditLogService.log(AuditAction.ORDER_CANCEL, "orders", cancelledOrder.getId(),
                Map.of("status", previousStatus.name()),
                Map.of("status", OrderStatus.CANCELLED.name(), "cancelReason", cancelledOrder.getCancelReason()));

        log.info("Thu ngân hủy đơn hàng ID={} lý do: {}", cancelledOrder.getId(), cancelledOrder.getCancelReason());

        return orderMapper.toDto(cancelledOrder);
    }

    private OrderDetailDto buildOrderDetailDto(Order order) {
        List<OrderLine> lines = orderLineRepository.findByOrderId(order.getId());
        List<OrderLineDto> lineDtos = lines.stream().map(line -> {
            List<OrderItemTopping> toppings = orderItemToppingRepository.findByOrderLineId(line.getId());
            List<OrderItemToppingDto> toppingDtos = toppings.stream().map(t -> {
                String toppingName = toppingRepository.findById(t.getToppingId()).map(Topping::getName).orElse(null);
                return orderMapper.toToppingDto(t, toppingName);
            }).toList();

            String productName = productRepository.findById(line.getProductId())
                    .map(Product::getName).orElse(null);
            return orderMapper.toLineDto(line, productName, toppingDtos);
        }).toList();

        OrderVoucherDto voucherDto = orderVoucherRepository.findByOrderId(order.getId())
                .map(ov -> {
                    String code = voucherRepository.findById(ov.getVoucherId())
                            .map(Voucher::getCode).orElse(null);
                    return orderMapper.toVoucherDto(ov, code);
                }).orElse(null);

        return orderMapper.toDetailDto(order, lineDtos, voucherDto);
    }
}
