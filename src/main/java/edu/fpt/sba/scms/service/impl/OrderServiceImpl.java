package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.SortDirection;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.ForbiddenException;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
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
import edu.fpt.sba.scms.model.Category;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.OrderItemTopping;
import edu.fpt.sba.scms.model.OrderLine;
import edu.fpt.sba.scms.model.OrderVoucher;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.ProductSize;
import edu.fpt.sba.scms.model.ProductTopping;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.model.Topping;
import edu.fpt.sba.scms.model.Voucher;
import edu.fpt.sba.scms.model.VoucherCategory;
import edu.fpt.sba.scms.model.VoucherProduct;
import edu.fpt.sba.scms.repository.CategoryRepository;
import edu.fpt.sba.scms.repository.OrderItemToppingRepository;
import edu.fpt.sba.scms.repository.OrderLineRepository;
import edu.fpt.sba.scms.repository.OrderRepository;
import edu.fpt.sba.scms.repository.OrderSpecs;
import edu.fpt.sba.scms.repository.OrderVoucherRepository;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.repository.ProductSizeRepository;
import edu.fpt.sba.scms.repository.ProductToppingRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.ToppingRepository;
import edu.fpt.sba.scms.repository.VoucherCategoryRepository;
import edu.fpt.sba.scms.repository.VoucherProductRepository;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderItemToppingRepository orderItemToppingRepository;
    private final OrderVoucherRepository orderVoucherRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherProductRepository voucherProductRepository;
    private final VoucherCategoryRepository voucherCategoryRepository;
    private final ProductRepository productRepository;
    private final ProductSizeRepository productSizeRepository;
    private final ProductToppingRepository productToppingRepository;
    private final CategoryRepository categoryRepository;
    private final ShiftRepository shiftRepository;
    private final ToppingRepository toppingRepository;
    private final AuditLogService auditLogService;
    private final OrderMapper orderMapper;

    private record ResolvedTopping(Topping topping, BigDecimal extraPrice) {
    }

    private record ResolvedOrderLine(
            CreateOrderLineRequest request,
            Product product,
            String effectiveSize,
            BigDecimal effectiveUnitPrice,
            List<ResolvedTopping> toppings,
            BigDecimal toppingTotal,
            BigDecimal lineTotal
    ) {
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailDto createOrder(CreateOrderRequest request, UserPrincipal principal) {
        Integer branchId = BranchContext.resolveBranchScope(request.branchId());
        ensureDrawerNotLockedForHandover(branchId != null ? branchId : 1);
        Integer cashierId = principal != null ? principal.userId() : null;

        List<CreateOrderLineRequest> lineRequests = resolveLineRequests(request);
        List<ResolvedOrderLine> resolvedLines = resolveOrderLines(lineRequests);
        BigDecimal totalAmount = calculateTotalAmount(resolvedLines, request);

        Order savedOrder = saveNewOrder(request, branchId, cashierId, totalAmount);
        List<OrderLineDto> lineDtos = saveAllLinesWithToppings(resolvedLines, savedOrder.getId());

        log.info("Tạo đơn hàng POS ID={} tại chi nhánh {}, tổng thanh toán={}",
                savedOrder.getId(), savedOrder.getBranchId(), savedOrder.getFinalAmount());

        return orderMapper.toDetailDto(savedOrder, lineDtos, null);
    }

    private void ensureDrawerNotLockedForHandover(Integer branchId) {
        if (branchId == null) {
            return;
        }
        shiftRepository.findFirstByBranchIdAndStatusOrderByOpenedAtDesc(branchId, ShiftStatus.OPEN)
                .ifPresent(s -> {
                    if (s.isHandoverPending()) {
                        throw new BusinessRuleException(BusinessErrorCode.SHIFT_HANDOVER_PENDING,
                                "Két tiền đang tạm khóa chờ xác nhận bàn giao giữa ca #" + s.getId()
                                        + ". Vui lòng kiểm két nhận ca trước khi tạo đơn POS mới.");
                    }
                    if (s.isManagerClosePending()) {
                        throw new BusinessRuleException(BusinessErrorCode.SHIFT_MANAGER_CLOSE_PENDING,
                                "Ca #" + s.getId()
                                        + " đã chốt kiểm két cuối ca và đang chờ Quản lý chi nhánh xác nhận nhận tiền / đóng ca. Quầy POS tạm khóa cho đến khi Quản lý xử lý.");
                    }
                    if (s.isEarlyClosePending()) {
                        throw new BusinessRuleException(BusinessErrorCode.SHIFT_EARLY_CLOSE_PENDING,
                                "Ca #" + s.getId()
                                        + " đang chờ Quản lý chi nhánh xác nhận đóng ca sớm. Quầy POS tạm khóa cho đến khi được duyệt hoặc hủy yêu cầu.");
                    }
                });
    }

    /** Trả về danh sách line từ request (rỗng nếu không có). */
    private List<CreateOrderLineRequest> resolveLineRequests(CreateOrderRequest request) {
        if (request.lines() == null || request.lines().isEmpty()) {
            return List.of();
        }
        return request.lines();
    }

    private List<ResolvedOrderLine> resolveOrderLines(List<CreateOrderLineRequest> lineRequests) {
        if (lineRequests.isEmpty()) {
            return List.of();
        }
        List<ResolvedOrderLine> resolved = new ArrayList<>(lineRequests.size());
        for (CreateOrderLineRequest lineReq : lineRequests) {
            Product product = requireAvailableProduct(lineReq.productId());
            ProductSizeName sizeEnum = resolveSizeEnum(lineReq.size(), product);
            String effectiveSize = sizeEnum != null ? sizeEnum.name() : null;
            BigDecimal effectiveUnitPrice = resolveUnitPrice(product, sizeEnum, lineReq.unitPrice());
            List<ResolvedTopping> toppings = resolveLineToppings(product.getId(), lineReq.toppingIds());
            BigDecimal toppingTotal = toppings.stream()
                    .map(ResolvedTopping::extraPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal lineTotal = OrderPricingCalculator.calculateLineTotal(
                    effectiveUnitPrice, toppingTotal, lineReq.quantity());
            resolved.add(new ResolvedOrderLine(
                    lineReq, product, effectiveSize, effectiveUnitPrice, toppings, toppingTotal, lineTotal));
        }
        return resolved;
    }

    /** Tính tổng tiền hàng từ các dòng đã kiểm tra & định giá theo DB (hoặc totalAmount fallback nếu không gửi lines). */
    private static BigDecimal calculateTotalAmount(List<ResolvedOrderLine> resolvedLines,
                                                   CreateOrderRequest request) {
        if (resolvedLines.isEmpty()) {
            return request.totalAmount() != null ? request.totalAmount() : BigDecimal.ZERO;
        }
        return resolvedLines.stream()
                .map(ResolvedOrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Khởi tạo và lưu entity Order mới. */
    private Order saveNewOrder(CreateOrderRequest request, Integer branchId,
                               Integer cashierId, BigDecimal totalAmount) {
        BigDecimal discountAmount = request.discountAmount() != null
                ? request.discountAmount() : BigDecimal.ZERO;
        Integer resolvedBranchId = branchId != null ? branchId : 1;
        Integer resolvedShiftId = request.shiftId() != null
                ? request.shiftId()
                : shiftRepository.findFirstByBranchIdAndStatusOrderByOpenedAtDesc(resolvedBranchId, ShiftStatus.OPEN)
                        .map(Shift::getId)
                        .orElse(null);

        Order order = Order.builder()
                .branchId(resolvedBranchId)
                .shiftId(resolvedShiftId)
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

    private List<OrderLineDto> saveAllLinesWithToppings(List<ResolvedOrderLine> resolvedLines, Integer orderId) {
        List<OrderLineDto> result = new ArrayList<>(resolvedLines.size());
        for (ResolvedOrderLine line : resolvedLines) {
            OrderLine savedLine = saveOrderLine(line, orderId);
            List<OrderItemToppingDto> toppingDtos = saveToppingsForLine(line.toppings(), savedLine.getId());
            result.add(orderMapper.toLineDto(savedLine, line.product().getName(), toppingDtos));
        }
        return result;
    }

    /** Tìm sản phẩm, kiểm tra danh mục hoạt động và trạng thái AVAILABLE. */
    private Product requireAvailableProduct(Integer productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("sản phẩm"));
        if (product.getStatus() != ProductStatus.AVAILABLE) {
            throw new BusinessRuleException(BusinessErrorCode.PRODUCT_UNAVAILABLE,
                    "Sản phẩm '" + product.getName() + "' hiện không khả dụng để đặt hàng.");
        }
        if (product.getCategoryId() != null) {
            Category category = categoryRepository.findById(product.getCategoryId()).orElse(null);
            if (category == null || category.isSystemCategory() || category.getStatus() != RecordStatus.ACTIVE) {
                throw new BusinessRuleException(BusinessErrorCode.PRODUCT_UNAVAILABLE,
                        "Danh mục của sản phẩm '" + product.getName() + "' hiện ngừng phục vụ.");
            }
        }
        return product;
    }

    private static ProductSizeName resolveSizeEnum(String rawSize, Product product) {
        if (rawSize != null && !rawSize.isBlank()) {
            try {
                return ProductSizeName.valueOf(rawSize.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new InvalidFieldValueException("lines", "Size không hợp lệ: " + rawSize);
            }
        }
        return product.getDefaultSize();
    }

    private BigDecimal resolveUnitPrice(Product product, ProductSizeName sizeEnum, BigDecimal fallbackPrice) {
        List<ProductSize> configuredSizes = productSizeRepository.findAllByProductIdOrderBySizeAsc(product.getId());
        if (configuredSizes.isEmpty()) {
            return fallbackPrice;
        }
        if (sizeEnum == null) {
            throw new InvalidFieldValueException("lines",
                    "Vui lòng chọn size cho sản phẩm '" + product.getName() + "'.");
        }
        ProductSize matched = configuredSizes.stream()
                .filter(ps -> ps.getSize() == sizeEnum)
                .findFirst()
                .orElseThrow(() -> new InvalidFieldValueException("lines",
                        "Size " + sizeEnum + " không khả dụng cho sản phẩm '" + product.getName() + "'."));
        return matched.getPrice();
    }

    private List<ResolvedTopping> resolveLineToppings(Integer productId, List<Integer> toppingIds) {
        if (toppingIds == null || toppingIds.isEmpty()) {
            return List.of();
        }
        List<Integer> distinctIds = toppingIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Integer, Topping> foundById = toppingRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(Topping::getId, Function.identity()));
        if (foundById.size() != distinctIds.size()) {
            throw new InvalidFieldValueException("lines", "Có topping không tồn tại.");
        }
        Map<Integer, ProductTopping> customByToppingId = productToppingRepository.findAllByProductId(productId).stream()
                .collect(Collectors.toMap(ProductTopping::getToppingId, Function.identity(), (a, b) -> a));

        List<ResolvedTopping> result = new ArrayList<>(distinctIds.size());
        for (Integer tid : distinctIds) {
            Topping topping = foundById.get(tid);
            if (topping.getStatus() != RecordStatus.ACTIVE) {
                throw new BusinessRuleException(BusinessErrorCode.PRODUCT_UNAVAILABLE,
                        "Topping '" + topping.getName() + "' hiện ngừng phục vụ.");
            }
            ProductTopping custom = customByToppingId.get(tid);
            BigDecimal extraPrice = (custom != null && custom.getExtraPrice() != null)
                    ? custom.getExtraPrice()
                    : topping.getDefaultPrice();
            result.add(new ResolvedTopping(topping, extraPrice != null ? extraPrice : BigDecimal.ZERO));
        }
        return result;
    }

    private OrderLine saveOrderLine(ResolvedOrderLine resolvedLine, Integer orderId) {
        CreateOrderLineRequest lineReq = resolvedLine.request();
        OrderLine line = OrderLine.builder()
                .orderId(orderId)
                .productId(resolvedLine.product().getId())
                .quantity(lineReq.quantity())
                .size(resolvedLine.effectiveSize())
                .unitPrice(resolvedLine.effectiveUnitPrice())
                .toppingTotal(resolvedLine.toppingTotal())
                .sugarLevel(lineReq.sugarLevel())
                .iceLevel(lineReq.iceLevel())
                .note(lineReq.note())
                .lineTotal(resolvedLine.lineTotal())
                .build();
        return orderLineRepository.save(line);
    }

    private List<OrderItemToppingDto> saveToppingsForLine(List<ResolvedTopping> toppings, Integer orderLineId) {
        List<OrderItemToppingDto> dtos = new ArrayList<>(toppings.size());
        for (ResolvedTopping resolved : toppings) {
            OrderItemTopping saved = orderItemToppingRepository.save(
                    OrderItemTopping.builder()
                            .orderLineId(orderLineId)
                            .toppingId(resolved.topping().getId())
                            .extraPrice(resolved.extraPrice())
                            .build());
            dtos.add(orderMapper.toToppingDto(saved, resolved.topping().getName()));
        }
        return dtos;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderDto> listOrders(OrderListQuery query, UserPrincipal principal) {
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

        Integer scopedShiftId = null;
        if (!isManagerOrCoo(principal)) {
            Integer nonManagerBranchId = branchId != null ? branchId : BranchContext.requireBranchId();
            Shift openShift = shiftRepository
                    .findFirstByBranchIdAndStatusOrderByOpenedAtDesc(nonManagerBranchId, ShiftStatus.OPEN)
                    .orElse(null);
            if (openShift == null) {
                return Page.empty(pageable);
            }
            scopedShiftId = openShift.getId();
        }

        Page<Order> page = orderRepository.findAll(
                OrderSpecs.filter(branchId, scopedShiftId, query.status(), query.fromDate(), query.toDate(), query.keyword()),
                pageable);

        return page.map(orderMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailDto getOrderById(Integer id, UserPrincipal principal) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("đơn hàng"));

        Integer scopedBranchId = BranchContext.resolveBranchScope(order.getBranchId());
        if (!isManagerOrCoo(principal)) {
            Integer nonManagerBranchId = scopedBranchId != null ? scopedBranchId : order.getBranchId();
            Shift openShift = shiftRepository
                    .findFirstByBranchIdAndStatusOrderByOpenedAtDesc(nonManagerBranchId, ShiftStatus.OPEN)
                    .orElse(null);
            if (openShift == null || !Objects.equals(openShift.getId(), order.getShiftId())) {
                throw new ForbiddenException(ErrorCode.ACCESS_DENIED,
                        "Thu ngân chỉ được xem đơn hàng thuộc ca hiện tại đang mở.");
            }
        }

        return buildOrderDetailDto(order);
    }

    private static boolean isManagerOrCoo(UserPrincipal principal) {
        if (principal == null) {
            return true;
        }
        String role = principal.role();
        return Roles.COO.code().equals(role) || Roles.BRANCH_MANAGER.code().equals(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailDto applyVoucher(Integer id, ApplyVoucherRequest request) {
        Order order = orderRepository.findByIdForUpdate(id)
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

        Voucher voucher = voucherRepository.findByCodeForUpdate(request.code().trim().toUpperCase(Locale.ROOT))
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

        validateVoucherScope(voucher.getId(), order.getId());

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

    private void validateVoucherScope(Integer voucherId, Integer orderId) {
        List<Integer> allowedProductIds = voucherProductRepository.findAllByVoucherId(voucherId).stream()
                .map(VoucherProduct::getProductId)
                .toList();
        List<Integer> allowedCategoryIds = voucherCategoryRepository.findAllByVoucherId(voucherId).stream()
                .map(VoucherCategory::getCategoryId)
                .toList();
        if (allowedProductIds.isEmpty() && allowedCategoryIds.isEmpty()) {
            return;
        }
        List<OrderLine> lines = orderLineRepository.findByOrderId(orderId);
        Set<Integer> orderProductIds = lines.stream()
                .map(OrderLine::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Integer> orderCategoryIds = orderProductIds.isEmpty()
                ? Set.of()
                : productRepository.findAllById(orderProductIds).stream()
                        .map(Product::getCategoryId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        if (!VoucherEligibilityPolicy.isScopeEligible(
                allowedProductIds, allowedCategoryIds, orderProductIds, orderCategoryIds)) {
            throw new BusinessRuleException(BusinessErrorCode.VOUCHER_SCOPE_NOT_MET);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailDto removeVoucher(Integer id) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("đơn hàng"));

        BranchContext.resolveBranchScope(order.getBranchId());

        if (!OrderStatePolicy.canRemoveVoucher(order.getStatus())) {
            throw new BusinessRuleException(BusinessErrorCode.ORDER_FROZEN,
                    "Không thể gỡ voucher khỏi đơn hàng đã thanh toán hoặc đã hủy.");
        }

        orderVoucherRepository.findByOrderId(order.getId()).ifPresent(ov -> {
            voucherRepository.findByIdForUpdate(ov.getVoucherId()).ifPresent(v -> {
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
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("đơn hàng"));

        BranchContext.resolveBranchScope(order.getBranchId());

        if (order.isPaid()) {
            throw new BusinessRuleException(BusinessErrorCode.ORDER_FROZEN,
                    "Đơn hàng đã thanh toán (PAID) không thể hủy trực tiếp, phải qua quy trình hoàn tiền.");
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
                    "Lý do hủy đơn hàng là bắt buộc.");
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(request.cancelReason().trim());
        order.setCancelledAt(Instant.now());
        Order cancelledOrder = orderRepository.save(order);

        // Gỡ lượt dùng voucher nếu có
        orderVoucherRepository.findByOrderId(order.getId()).ifPresent(ov -> {
            voucherRepository.findByIdForUpdate(ov.getVoucherId()).ifPresent(v -> {
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
