package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.StockTransactionType;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.ConflictException;
import edu.fpt.sba.scms.common.exception.ForbiddenException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.common.exception.ValidationException;
import edu.fpt.sba.scms.common.util.MoneyUtil;
import edu.fpt.sba.scms.dto.request.CreateReceiveSlipRequest;
import edu.fpt.sba.scms.dto.response.StockReceiveSlipResponse;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Ingredient;
import edu.fpt.sba.scms.model.Inventory;
import edu.fpt.sba.scms.model.StockTransaction;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.IngredientRepository;
import edu.fpt.sba.scms.repository.InventoryRepository;
import edu.fpt.sba.scms.repository.StockTransactionRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.security.BranchContext;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.StockTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Triển khai nghiệp vụ UC28 — Nhập kho nguyên vật liệu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockTransactionServiceImpl implements StockTransactionService {

    private static final String ENTITY_STOCK_TRANSACTION = "stock_transaction";
    private static final BigDecimal VAT_RATE = new BigDecimal("0.10");

    private final StockTransactionRepository stockTransactionRepository;
    private final InventoryRepository inventoryRepository;
    private final IngredientRepository ingredientRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockReceiveSlipResponse receiveStock(CreateReceiveSlipRequest request) {
        // 1. Kiểm tra ngữ cảnh chi nhánh (BR-01)
        Integer branchId = BranchContext.requireBranchId();
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("chi nhánh"));

        // Lấy thông tin người thực hiện giao dịch
        User currentUser = resolveCurrentUser();

        // 2. Validate dữ liệu đầu vào (Fail Fast)
        validateRequest(request);

        // 3. Kiểm tra chống trùng mã hóa đơn trong 7 ngày (AF-03)
        if (request.invoiceRef() != null && !request.invoiceRef().isBlank()) {
            if (stockTransactionRepository.existsByBranchIdAndInvoiceRef(branchId, request.invoiceRef())) {
                throw new ConflictException(ErrorCode.RESOURCE_CONFLICT,
                        "Số hóa đơn " + request.invoiceRef() + " đã được nhập kho trước đó.");
            }
        }

        // 4. Sinh mã phiếu nhập kho tự động PNK-YYYYMMDD-XXX
        String slipCode = generateSlipCode(branchId, "PNK");
        Instant now = Instant.now();

        BigDecimal subtotal = BigDecimal.ZERO;
        List<StockReceiveSlipResponse.ItemResponse> itemResponses = new ArrayList<>();

        // 5. Xử lý từng mặt hàng nhập kho
        for (CreateReceiveSlipRequest.Item itemReq : request.items()) {
            Ingredient ingredient = ingredientRepository.findById(itemReq.ingredientId())
                    .orElseThrow(() -> new NotFoundException("nguyên vật liệu"));

            if (!"ACTIVE".equalsIgnoreCase(ingredient.getStatus())) {
                throw new BusinessRuleException(BusinessErrorCode.PRODUCT_UNAVAILABLE,
                        "Nguyên liệu " + ingredient.getName() + " đang bị ngưng sử dụng, không thể nhập kho.");
            }

            // Khóa Pessimistic Lock trên dòng tồn kho để đảm bảo toàn vẹn dữ liệu
            Inventory inventory = inventoryRepository.findForUpdate(branchId, ingredient.getId())
                    .orElseGet(() -> createDefaultInventory(branch, ingredient));

            BigDecimal unitsPerPack = ingredient.getUnitsPerPack() != null
                    && ingredient.getUnitsPerPack().compareTo(BigDecimal.ZERO) > 0
                    ? ingredient.getUnitsPerPack() : BigDecimal.ONE;

            BigDecimal cartons = itemReq.cartons() != null ? itemReq.cartons() : BigDecimal.ZERO;
            BigDecimal loose = itemReq.looseUnits() != null ? itemReq.looseUnits() : BigDecimal.ZERO;

            // Tính toán quy đổi theo BR-40
            BigDecimal baseQtyAdded = cartons.multiply(unitsPerPack).add(loose);

            BigDecimal lineTotal = cartons.multiply(itemReq.purchasePrice());
            if (loose.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal unitPricePerBase = itemReq.purchasePrice().divide(unitsPerPack, 2, RoundingMode.HALF_UP);
                lineTotal = lineTotal.add(loose.multiply(unitPricePerBase));
            }
            lineTotal = lineTotal.setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);

            // Cập nhật sổ kho & đánh giá lại ngưỡng an toàn (BR-45)
            inventory.setCurrentQtyBase(inventory.getCurrentQtyBase().add(baseQtyAdded));
            inventory.recalculateStockAndFlag(unitsPerPack);
            inventoryRepository.save(inventory);

            // Lưu bản ghi giao dịch (16 cột, thông tin hóa đơn và mã phiếu ghi trong reason)
            BigDecimal quantityPacks = baseQtyAdded.divide(unitsPerPack, 4, RoundingMode.HALF_UP);
            String itemReason = formatReason(slipCode, request.source(), request.supplierName(),
                    request.invoiceRef(), itemReq.expiryDate(), request.notes());

            StockTransaction tx = StockTransaction.builder()
                    .branch(branch)
                    .ingredient(ingredient)
                    .type(StockTransactionType.RECEIVE)
                    .shiftId(null)
                    .stocktakeId(null)
                    .createdBy(currentUser)
                    .createdAt(now)
                    .cartons(cartons)
                    .looseUnits(loose)
                    .purchasePrice(itemReq.purchasePrice())
                    .lineTotal(lineTotal)
                    .quantityPacks(quantityPacks)
                    .quantityBase(baseQtyAdded)
                    .reason(itemReason)
                    .evidenceImageUrl(null)
                    .build();
            stockTransactionRepository.save(tx);

            String ingredientCode = String.format("ING-%03d", ingredient.getId());

            itemResponses.add(new StockReceiveSlipResponse.ItemResponse(
                    tx.getId(),
                    ingredient.getId(),
                    ingredientCode,
                    ingredient.getName(),
                    ingredient.getPackUnit(),
                    ingredient.getBaseUnit(),
                    unitsPerPack,
                    cartons,
                    loose,
                    baseQtyAdded,
                    itemReq.purchasePrice(),
                    lineTotal,
                    inventory.getCurrentQtyBase(),
                    inventory.getCurrentPackUnits(),
                    inventory.getLowStockFlag()
            ));
        }

        // 6. Tính thuế VAT và tổng tiền phải trả (BR-40)
        BigDecimal vatAmount = subtotal.multiply(VAT_RATE).setScale(0, RoundingMode.HALF_UP);
        BigDecimal totalPayable = subtotal.add(vatAmount);

        // 7. Ghi nhật ký kiểm toán bất biến (BR-51)
        auditLogService.log(
                AuditAction.STOCK_RECEIVE,
                ENTITY_STOCK_TRANSACTION,
                slipCode,
                null,
                Map.of(
                        "slipCode", slipCode,
                        "supplierName", request.supplierName(),
                        "totalPayable", totalPayable,
                        "itemCount", request.items().size(),
                        "summary", String.format("Tạo phiếu nhập kho %s từ NCC %s, tổng giá trị: %s VND",
                                slipCode, request.supplierName(), MoneyUtil.format(totalPayable))
                )
        );

        String receivedByName = currentUser.getFullName() != null && !currentUser.getFullName().isBlank()
                ? currentUser.getFullName() : currentUser.getUsername();

        return new StockReceiveSlipResponse(
                slipCode,
                branchId,
                branch.getName(),
                request.source(),
                request.supplierName(),
                request.invoiceRef(),
                now,
                receivedByName,
                subtotal,
                vatAmount,
                totalPayable,
                request.notes(),
                itemResponses
        );
    }

    private void validateRequest(CreateReceiveSlipRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw ValidationException.single("items", "Danh sách mặt hàng nhập kho không được để trống.");
        }

        for (CreateReceiveSlipRequest.Item item : request.items()) {
            BigDecimal cartons = item.cartons() != null ? item.cartons() : BigDecimal.ZERO;
            BigDecimal loose = item.looseUnits() != null ? item.looseUnits() : BigDecimal.ZERO;

            if (cartons.compareTo(BigDecimal.ZERO) < 0) {
                throw ValidationException.single("cartons", "Số lượng kiện không được âm.");
            }
            if (loose.compareTo(BigDecimal.ZERO) < 0) {
                throw ValidationException.single("looseUnits", "Số lượng lẻ không được âm.");
            }
            if (cartons.compareTo(BigDecimal.ZERO) == 0 && loose.compareTo(BigDecimal.ZERO) == 0) {
                throw ValidationException.single("items", "Số lượng nhập của mặt hàng phải lớn hơn 0.");
            }
            if (item.purchasePrice() == null || item.purchasePrice().compareTo(BigDecimal.ZERO) < 0) {
                throw ValidationException.single("purchasePrice", "Đơn giá mua không được âm.");
            }
        }
    }

    private User resolveCurrentUser() {
        Integer userId = BranchContext.current().map(BranchContext.Info::userId).orElse(null);
        if (userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                return user;
            }
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            User user = userRepository.findById(principal.userId()).orElse(null);
            if (user != null) {
                return user;
            }
        }

        throw new ForbiddenException(ErrorCode.AUTH_REQUIRED);
    }

    private Inventory createDefaultInventory(Branch branch, Ingredient ingredient) {
        Inventory inv = Inventory.builder()
                .branch(branch)
                .ingredient(ingredient)
                .currentQtyBase(BigDecimal.ZERO)
                .currentPackUnits(BigDecimal.ZERO)
                .safetyStockLevel(BigDecimal.ZERO)
                .lowStockFlag(true)
                .lastUpdated(Instant.now())
                .build();
        return inventoryRepository.saveAndFlush(inv);
    }

    private String generateSlipCode(Integer branchId, String prefix) {
        String dateStr = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        long countToday = stockTransactionRepository.countTodaySlipsByPrefix(branchId, prefix + "-" + dateStr);
        return String.format("%s-%s-%03d", prefix, dateStr, countToday + 1);
    }

    private String formatReason(String slipCode, String source, String supplierName,
                                String invoiceRef, LocalDate expiryDate, String notes) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(slipCode).append("]");
        if (invoiceRef != null && !invoiceRef.isBlank()) {
            sb.append(" [HĐ: ").append(invoiceRef.trim()).append("]");
        }
        if (supplierName != null && !supplierName.isBlank()) {
            sb.append(" NCC: ").append(supplierName.trim());
        }
        if (source != null && !source.isBlank()) {
            sb.append(" | Nguồn: ").append(source.trim());
        }
        if (expiryDate != null) {
            sb.append(" | HSD: ").append(expiryDate);
        }
        if (notes != null && !notes.isBlank()) {
            sb.append(" | Ghi chú: ").append(notes.trim());
        }

        String result = sb.toString();
        if (result.length() > 500) {
            return result.substring(0, 500);
        }
        return result;
    }
}
