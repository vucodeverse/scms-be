package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.SecurityAlertSeverity;
import edu.fpt.sba.scms.common.enums.SecurityAlertStatus;
import edu.fpt.sba.scms.common.enums.SecurityAlertType;
import edu.fpt.sba.scms.common.enums.StockTransactionType;
import edu.fpt.sba.scms.common.enums.StocktakeStatus;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.ConflictException;
import edu.fpt.sba.scms.common.exception.ForbiddenException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.common.exception.ValidationException;
import edu.fpt.sba.scms.common.util.MoneyUtil;
import edu.fpt.sba.scms.dto.request.CreateStocktakeSessionRequest;
import edu.fpt.sba.scms.dto.request.SubmitStocktakeCountsRequest;
import edu.fpt.sba.scms.dto.response.StocktakeReconciliationDto;
import edu.fpt.sba.scms.dto.response.StocktakeSessionDto;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Ingredient;
import edu.fpt.sba.scms.model.Inventory;
import edu.fpt.sba.scms.model.SecurityAlert;
import edu.fpt.sba.scms.model.StockTransaction;
import edu.fpt.sba.scms.model.Stocktake;
import edu.fpt.sba.scms.model.StocktakeLine;
import edu.fpt.sba.scms.model.StocktakeLineId;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.InventoryRepository;
import edu.fpt.sba.scms.repository.SecurityAlertRepository;
import edu.fpt.sba.scms.repository.StockTransactionRepository;
import edu.fpt.sba.scms.repository.StocktakeLineRepository;
import edu.fpt.sba.scms.repository.StocktakeRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.security.BranchContext;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.StocktakeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Triển khai nghiệp vụ UC31 — Mở đợt kiểm kê & UC32 — Cân đối chênh lệch sau kiểm kê.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StocktakeServiceImpl implements StocktakeService {

    private static final String ENTITY_STOCKTAKE = "stocktake";

    private final StocktakeRepository stocktakeRepository;
    private final StocktakeLineRepository stocktakeLineRepository;
    private final InventoryRepository inventoryRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final SecurityAlertRepository securityAlertRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeSessionDto createStocktakeSession(CreateStocktakeSessionRequest request) {
        return createStocktakeSession(null, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeSessionDto createStocktakeSession(Integer branchIdParam, CreateStocktakeSessionRequest request) {
        Integer branchId = branchIdParam != null
                ? branchIdParam
                : BranchContext.current().map(BranchContext.Info::branchId).orElse(null);

        if (branchId == null) {
            branchId = resolveBranchIdForUser();
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("chi nhánh"));

        User currentUser = resolveCurrentUser();

        // BR-44: Chặn tuyệt đối mở đợt kép khi đang có đợt OPEN
        if (stocktakeRepository.existsByBranch_IdAndStatus(branchId, StocktakeStatus.OPEN)) {
            Stocktake active = stocktakeRepository.findByBranch_IdAndStatus(branchId, StocktakeStatus.OPEN)
                    .orElseThrow();
            String activeSlipCode = formatSlipCode(active);
            throw new ConflictException(ErrorCode.ACTIVE_STOCKTAKE_EXISTS,
                    String.format("Chi nhánh đang có một phiên kiểm kê đang mở (Mã: %s). Vui lòng hoàn tất hoặc hủy phiên cũ trước khi mở phiên mới.", activeSlipCode));
        }

        // Lấy danh mục tồn kho hiện tại của chi nhánh (chỉ lấy các nguyên liệu active)
        List<Inventory> inventories = inventoryRepository.findAllByBranchIdWithActiveIngredient(branchId);
        if (inventories.isEmpty()) {
            throw new BusinessRuleException(BusinessErrorCode.EMPTY_INVENTORY,
                    "Chi nhánh chưa có danh mục tồn kho để thực hiện kiểm kê.");
        }

        Instant now = Instant.now();
        Stocktake stocktake = Stocktake.builder()
                .branch(branch)
                .startedBy(currentUser)
                .startedAt(now)
                .status(StocktakeStatus.OPEN)
                .build();
        Stocktake saved = stocktakeRepository.save(stocktake);

        // Snapshot tức thời book_quantity từ inventory vào stocktake_line (Rule 3)
        List<StocktakeLine> lines = inventories.stream().map(inv -> StocktakeLine.builder()
                .id(new StocktakeLineId(saved.getId(), inv.getIngredient().getId()))
                .stocktake(saved)
                .ingredient(inv.getIngredient())
                .bookQuantity(inv.getCurrentQtyBase() != null ? inv.getCurrentQtyBase() : BigDecimal.ZERO)
                .countedQuantity(null)
                .variance(null)
                .build()
        ).collect(Collectors.toList());

        saved.setLines(lines);
        Stocktake updated = stocktakeRepository.save(saved);

        String slipCode = formatSlipCode(updated);
        String roundType = request != null && request.roundType() != null && !request.roundType().isBlank()
                ? request.roundType() : "DAILY_CLOSING";
        String witnessName = request != null ? request.witnessName() : null;

        // BR-51: Ghi nhận nhật ký kiểm toán bất biến
        auditLogService.log(
                AuditAction.STOCKTAKE_START,
                ENTITY_STOCKTAKE,
                updated.getId(),
                null,
                Map.of(
                        "stocktakeId", updated.getId(),
                        "slipCode", slipCode,
                        "roundType", roundType,
                        "witnessName", witnessName != null ? witnessName : "",
                        "totalItems", lines.size(),
                        "summary", String.format("Mở đợt kiểm kê kho %s (%s). Người làm chứng: %s",
                                slipCode, roundType, witnessName != null ? witnessName : "Không")
                )
        );

        return toSessionDto(updated, roundType, witnessName, false);
    }

    @Override
    @Transactional(readOnly = true)
    public StocktakeSessionDto getActiveStocktakeSession() {
        return getActiveStocktakeSession(null);
    }

    @Override
    @Transactional(readOnly = true)
    public StocktakeSessionDto getActiveStocktakeSession(Integer branchIdParam) {
        Integer branchId = branchIdParam != null
                ? branchIdParam
                : BranchContext.current().map(BranchContext.Info::branchId).orElse(null);
        if (branchId == null) {
            branchId = resolveBranchIdForUser();
        }

        // Rule 1: Bảo mật kiểm kê mù - Khi session đang OPEN, bookQuantity trả về null
        return stocktakeRepository.findByBranch_IdAndStatus(branchId, StocktakeStatus.OPEN)
                .map(st -> toSessionDto(st, null, null, true))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public StocktakeSessionDto getStocktakeSession(Long stocktakeId) {
        Integer branchId = BranchContext.current().map(BranchContext.Info::branchId).orElseGet(this::resolveBranchIdForUser);
        Stocktake stocktake = stocktakeRepository.findByIdAndBranch_Id(stocktakeId, branchId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.STOCKTAKE_NOT_FOUND,
                        "Không tìm thấy đợt kiểm kê tương ứng."));
        boolean hideBook = stocktake.getStatus() == StocktakeStatus.OPEN;
        return toSessionDto(stocktake, null, null, hideBook);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeReconciliationDto reconcileStocktake(Long stocktakeId, SubmitStocktakeCountsRequest request) {
        validateAdjustRequest(request);

        // Rule 2: Nguyên tắc 4 mắt - Bắt buộc có witnessName (@NotBlank)
        if (request.witnessName() == null || request.witnessName().trim().isBlank()) {
            throw ValidationException.single("witnessName",
                    "Họ tên Barista làm chứng là bắt buộc theo nguyên tắc 4 mắt (Rule 2).");
        }
        String witnessName = request.witnessName().trim();

        Integer branchId = BranchContext.current().map(BranchContext.Info::branchId).orElseGet(this::resolveBranchIdForUser);
        User currentUser = resolveCurrentUser();

        Stocktake stocktake = stocktakeRepository.findByIdAndBranch_Id(stocktakeId, branchId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.STOCKTAKE_NOT_FOUND,
                        "Không tìm thấy đợt kiểm kê tương ứng."));

        if (stocktake.getStatus() != StocktakeStatus.OPEN) {
            throw new ConflictException(ErrorCode.STOCKTAKE_ALREADY_CLOSED,
                    "Đợt kiểm kê này đã được cân đối hoặc đóng trước đó (Trạng thái: " + stocktake.getStatus() + ").");
        }

        // AF-03: Kiểm tra đủ số lượng đếm của toàn bộ danh mục kiểm kê
        Set<Long> submittedIngredientIds = request.counts().stream()
                .map(SubmitStocktakeCountsRequest.CountItem::ingredientId)
                .collect(Collectors.toSet());
        for (StocktakeLine line : stocktake.getLines()) {
            if (!submittedIngredientIds.contains(line.getIngredient().getId())) {
                throw ValidationException.single("counts",
                        "Vui lòng nhập đủ số lượng đếm của toàn bộ danh mục kiểm kê.");
            }
        }

        Map<Long, StocktakeLine> lineMap = stocktake.getLines().stream()
                .collect(Collectors.toMap(l -> l.getIngredient().getId(), Function.identity()));

        Instant now = Instant.now();
        int discrepancyCount = 0;
        int matchedCount = 0;
        BigDecimal netVarianceValue = BigDecimal.ZERO;
        List<StocktakeReconciliationDto.AdjustmentDetail> details = new ArrayList<>();
        String slipCode = formatSlipCode(stocktake);

        // Tính toán chênh lệch từng nguyên liệu
        for (SubmitStocktakeCountsRequest.CountItem countItem : request.counts()) {
            StocktakeLine line = lineMap.get(countItem.ingredientId());
            if (line == null) {
                continue;
            }

            BigDecimal counted = countItem.countedQuantity();
            BigDecimal book = line.getBookQuantity() != null ? line.getBookQuantity() : BigDecimal.ZERO;
            BigDecimal variance = counted.subtract(book);

            line.setCountedQuantity(counted);
            line.setVariance(variance);

            Ingredient ingredient = line.getIngredient();
            BigDecimal unitCost = ingredient.getUnitCost() != null ? ingredient.getUnitCost() : BigDecimal.ZERO;
            BigDecimal varianceFinancialValue = variance.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
            netVarianceValue = netVarianceValue.add(varianceFinancialValue);

            String statusStr;
            if (variance.compareTo(BigDecimal.ZERO) == 0) {
                matchedCount++;
                statusStr = "MATCHED";
            } else {
                discrepancyCount++;
                statusStr = variance.compareTo(BigDecimal.ZERO) < 0 ? "DEFICIT" : "SURPLUS";
            }

            details.add(new StocktakeReconciliationDto.AdjustmentDetail(
                    ingredient.getId(),
                    ingredient.getName(),
                    ingredient.getBaseUnit(),
                    book,
                    counted,
                    variance,
                    unitCost,
                    varianceFinancialValue,
                    statusStr,
                    countItem.notes()
            ));
        }

        // Rule 4: Ngưỡng dung sai 50.000 VNĐ
        BigDecimal TOLERANCE = new BigDecimal("50000");
        boolean isWithinTolerance = netVarianceValue.abs().compareTo(TOLERANCE) <= 0;

        if (isWithinTolerance) {
            // TRƯỜNG HỢP A: Trong dung sai <= 50.000 VNĐ -> Cho phép cân đối kho
            for (SubmitStocktakeCountsRequest.CountItem countItem : request.counts()) {
                StocktakeLine line = lineMap.get(countItem.ingredientId());
                if (line == null) continue;

                BigDecimal counted = countItem.countedQuantity();
                BigDecimal variance = line.getVariance();
                Ingredient ingredient = line.getIngredient();
                BigDecimal unitsPerPack = ingredient.getUnitsPerPack() != null && ingredient.getUnitsPerPack().compareTo(BigDecimal.ZERO) > 0
                        ? ingredient.getUnitsPerPack() : BigDecimal.ONE;

                // Khóa dòng tồn kho chống tranh chấp
                Inventory inventory = inventoryRepository.findForUpdate(branchId, countItem.ingredientId())
                        .orElseThrow(() -> new NotFoundException(ErrorCode.INVENTORY_NOT_FOUND,
                                "Không tìm thấy dữ liệu tồn kho của nguyên liệu ID: " + countItem.ingredientId()));

                inventory.setCurrentQtyBase(counted);
                inventory.recalculateStockAndFlag(unitsPerPack);
                inventoryRepository.save(inventory);

                if (variance.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal qtyPacks = variance.divide(unitsPerPack, 4, RoundingMode.HALF_UP);
                    BigDecimal unitCost = ingredient.getUnitCost() != null ? ingredient.getUnitCost() : BigDecimal.ZERO;
                    BigDecimal lineLoss = variance.abs().multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
                    String itemNotes = countItem.notes() != null && !countItem.notes().isBlank()
                            ? countItem.notes().trim() : (variance.compareTo(BigDecimal.ZERO) < 0 ? "DEFICIT" : "SURPLUS");
                    String txReason = String.format("Cân đối kiểm kê %s (%s %s). Người làm chứng: %s. Ghi chú: %s",
                            slipCode, variance, ingredient.getBaseUnit(), witnessName, itemNotes);
                    if (txReason.length() > 500) {
                        txReason = txReason.substring(0, 500);
                    }

                    StockTransaction adjustTx = StockTransaction.builder()
                            .branch(stocktake.getBranch())
                            .ingredient(ingredient)
                            .type(StockTransactionType.ADJUST)
                            .shiftId(null)
                            .stocktakeId(stocktake.getId())
                            .createdBy(currentUser)
                            .createdAt(now)
                            .cartons(null)
                            .looseUnits(null)
                            .purchasePrice(unitCost)
                            .lineTotal(lineLoss)
                            .quantityPacks(qtyPacks)
                            .quantityBase(variance)
                            .reason(txReason)
                            .evidenceImageUrl(null)
                            .build();
                    stockTransactionRepository.save(adjustTx);
                }
            }

            stocktake.setStatus(StocktakeStatus.ADJUSTED);
            stocktake.setClosedAt(now);
            stocktakeRepository.save(stocktake);
            stocktakeLineRepository.saveAll(stocktake.getLines());

            auditLogService.log(
                    AuditAction.STOCKTAKE_ADJUST,
                    ENTITY_STOCKTAKE,
                    stocktake.getId(),
                    null,
                    Map.of(
                            "stocktakeId", stocktake.getId(),
                            "slipCode", slipCode,
                            "witnessName", witnessName,
                            "matchedCount", matchedCount,
                            "discrepancyCount", discrepancyCount,
                            "netVarianceValue", netVarianceValue,
                            "toleranceStatus", "WITHIN_TOLERANCE",
                            "summary", String.format("Cân đối kiểm kê %s hợp lệ (trong dung sai <= 50.000đ): %d món khớp, %d món lệch. Chênh lệch ròng: %s VNĐ. Người làm chứng: %s",
                                    slipCode, matchedCount, discrepancyCount, MoneyUtil.format(netVarianceValue), witnessName)
                    )
            );

            return new StocktakeReconciliationDto(
                    stocktake.getId(),
                    slipCode,
                    "BALANCED",
                    now,
                    stocktake.getLines().size(),
                    matchedCount,
                    discrepancyCount,
                    netVarianceValue,
                    details
            );
        } else {
            // TRƯỜNG HỢP B: Vượt dung sai > 50.000 VNĐ -> KHÓA cân đối, chuyển DISCREPANCY_ALERT, phát SecurityAlert
            stocktake.setStatus(StocktakeStatus.DISCREPANCY_ALERT);
            stocktake.setClosedAt(now);
            stocktakeRepository.save(stocktake);
            stocktakeLineRepository.saveAll(stocktake.getLines());

            Branch branch = stocktake.getBranch();
            SecurityAlert alert = SecurityAlert.builder()
                    .branchId(branchId)
                    .branchName(branch.getName())
                    .alertType(SecurityAlertType.STOCKTAKE_DISCREPANCY)
                    .severity(SecurityAlertSeverity.HIGH)
                    .status(SecurityAlertStatus.ACTIVE)
                    .failureCount(1)
                    .title("Cảnh báo chênh lệch kiểm kê ca vượt ngưỡng 50.000 VNĐ (" + slipCode + ")")
                    .message(String.format("Phiên kiểm kê %s tại chi nhánh %s bị lệch ròng %s VNĐ (vượt dung sai cho phép 50.000 VNĐ). Người kiểm kê: %s, Barista làm chứng: %s. Chênh lệch: %d món. Hệ thống đã khóa cân đối kho tự động, yêu cầu giải trình và lập biên bản bồi thường.",
                            slipCode, branch.getName(), MoneyUtil.format(netVarianceValue),
                            currentUser != null ? currentUser.getFullName() : "Quản lý quán",
                            witnessName, discrepancyCount))
                    .targetUserId(currentUser != null ? currentUser.getId() : null)
                    .targetUserName(currentUser != null ? currentUser.getFullName() : null)
                    .targetRole(currentUser != null && currentUser.getPosition() != null ? currentUser.getPosition().name() : Roles.BRANCH_MANAGER.code())
                    .createdAt(now)
                    .build();
            securityAlertRepository.save(alert);

            auditLogService.log(
                    AuditAction.STOCKTAKE_ADJUST,
                    ENTITY_STOCKTAKE,
                    stocktake.getId(),
                    null,
                    Map.of(
                            "stocktakeId", stocktake.getId(),
                            "slipCode", slipCode,
                            "witnessName", witnessName,
                            "matchedCount", matchedCount,
                            "discrepancyCount", discrepancyCount,
                            "netVarianceValue", netVarianceValue,
                            "toleranceStatus", "DISCREPANCY_EXCEEDED",
                            "summary", String.format("CẢNH BÁO: Kiểm kê %s lệch ròng %s VNĐ vượt ngưỡng 50.000đ! Đã khóa cân đối và phát SecurityAlert HIGH lên COO.",
                                    slipCode, MoneyUtil.format(netVarianceValue))
                    )
            );

            log.warn("Kiểm kê {} vượt dung sai (|netVariance| = {} > 50.000 VNĐ). Khóa cân đối và kích hoạt SecurityAlert ID {}",
                    slipCode, netVarianceValue, alert.getId());

            return new StocktakeReconciliationDto(
                    stocktake.getId(),
                    slipCode,
                    "DISCREPANCY_ALERT",
                    now,
                    stocktake.getLines().size(),
                    matchedCount,
                    discrepancyCount,
                    netVarianceValue,
                    details
            );
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeReconciliationDto adjustInventory(Long stocktakeId, SubmitStocktakeCountsRequest request) {
        validateAdjustRequest(request);

        Integer branchId = BranchContext.requireBranchId();
        User currentUser = resolveCurrentUser();

        Stocktake stocktake = stocktakeRepository.findByIdAndBranch_Id(stocktakeId, branchId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.STOCKTAKE_NOT_FOUND,
                        "Không tìm thấy đợt kiểm kê tương ứng."));

        if (stocktake.getStatus() != StocktakeStatus.OPEN) {
            throw new ConflictException(ErrorCode.STOCKTAKE_ALREADY_CLOSED,
                    "Đợt kiểm kê này đã được cân đối hoặc đóng trước đó.");
        }

        // AF-03: Kiểm tra đủ số lượng đếm của toàn bộ danh mục kiểm kê
        Set<Long> submittedIngredientIds = request.counts().stream()
                .map(SubmitStocktakeCountsRequest.CountItem::ingredientId)
                .collect(Collectors.toSet());
        for (StocktakeLine line : stocktake.getLines()) {
            if (!submittedIngredientIds.contains(line.getIngredient().getId())) {
                throw ValidationException.single("counts",
                        "Vui lòng nhập đủ số lượng đếm của toàn bộ danh mục kiểm kê.");
            }
        }

        Map<Long, StocktakeLine> lineMap = stocktake.getLines().stream()
                .collect(Collectors.toMap(l -> l.getIngredient().getId(), Function.identity()));

        Instant now = Instant.now();
        int discrepancyCount = 0;
        int matchedCount = 0;
        BigDecimal netVarianceValue = BigDecimal.ZERO;
        List<StocktakeReconciliationDto.AdjustmentDetail> details = new ArrayList<>();
        String slipCode = formatSlipCode(stocktake);

        for (SubmitStocktakeCountsRequest.CountItem countItem : request.counts()) {
            StocktakeLine line = lineMap.get(countItem.ingredientId());
            if (line == null) {
                continue;
            }

            BigDecimal counted = countItem.countedQuantity();
            BigDecimal book = line.getBookQuantity() != null ? line.getBookQuantity() : BigDecimal.ZERO;
            BigDecimal variance = counted.subtract(book);

            line.setCountedQuantity(counted);
            line.setVariance(variance);

            // Khóa Pessimistic Write để cập nhật tồn kho thực tế tránh xung đột
            Inventory inventory = inventoryRepository.findForUpdate(branchId, countItem.ingredientId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.INVENTORY_NOT_FOUND,
                            "Không tìm thấy dữ liệu tồn kho của nguyên liệu ID: " + countItem.ingredientId()));

            Ingredient ingredient = line.getIngredient();
            BigDecimal unitsPerPack = ingredient.getUnitsPerPack() != null && ingredient.getUnitsPerPack().compareTo(BigDecimal.ZERO) > 0
                    ? ingredient.getUnitsPerPack() : BigDecimal.ONE;

            // Cân đè tồn kho thật: current_qty_base = counted
            inventory.setCurrentQtyBase(counted);
            inventory.recalculateStockAndFlag(unitsPerPack);
            inventoryRepository.save(inventory);

            BigDecimal unitCost = ingredient.getUnitCost() != null ? ingredient.getUnitCost() : BigDecimal.ZERO;
            BigDecimal varianceFinancialValue = variance.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
            netVarianceValue = netVarianceValue.add(varianceFinancialValue);

            String statusStr;
            if (variance.compareTo(BigDecimal.ZERO) == 0) {
                matchedCount++;
                statusStr = "MATCHED";
            } else {
                discrepancyCount++;
                statusStr = variance.compareTo(BigDecimal.ZERO) < 0 ? "DEFICIT" : "SURPLUS";

                // Rule BR-44: Tự động sinh bản ghi trong StockTransaction với type = 'ADJUST'
                BigDecimal qtyPacks = variance.divide(unitsPerPack, 4, RoundingMode.HALF_UP);
                String itemNotes = countItem.notes() != null && !countItem.notes().isBlank()
                        ? countItem.notes().trim() : statusStr;
                String txReason = String.format("Cân đối kiểm kê %s (%s %s). Ghi chú: %s",
                        slipCode, variance, ingredient.getBaseUnit(), itemNotes);
                if (txReason.length() > 500) {
                    txReason = txReason.substring(0, 500);
                }

                StockTransaction adjustTx = StockTransaction.builder()
                        .branch(stocktake.getBranch())
                        .ingredient(ingredient)
                        .type(StockTransactionType.ADJUST)
                        .shiftId(null)
                        .stocktakeId(stocktake.getId())
                        .createdBy(currentUser)
                        .createdAt(now)
                        .cartons(null)
                        .looseUnits(null)
                        .purchasePrice(unitCost)
                        .lineTotal(varianceFinancialValue.abs())
                        .quantityPacks(qtyPacks)
                        .quantityBase(variance)
                        .reason(txReason)
                        .evidenceImageUrl(null)
                        .build();
                stockTransactionRepository.save(adjustTx);
            }

            details.add(new StocktakeReconciliationDto.AdjustmentDetail(
                    ingredient.getId(),
                    ingredient.getName(),
                    ingredient.getBaseUnit(),
                    book,
                    counted,
                    variance,
                    unitCost,
                    varianceFinancialValue,
                    statusStr,
                    countItem.notes()
            ));
        }

        // Đổi trạng thái sang ADJUSTED và đóng đợt
        stocktake.setStatus(StocktakeStatus.ADJUSTED);
        stocktake.setClosedAt(now);
        stocktakeRepository.save(stocktake);

        // BR-51: Ghi nhận nhật ký kiểm toán
        auditLogService.log(
                AuditAction.STOCKTAKE_ADJUST,
                ENTITY_STOCKTAKE,
                stocktake.getId(),
                null,
                Map.of(
                        "stocktakeId", stocktake.getId(),
                        "slipCode", slipCode,
                        "totalItems", stocktake.getLines().size(),
                        "matchedCount", matchedCount,
                        "discrepancyCount", discrepancyCount,
                        "netVarianceValue", netVarianceValue,
                        "summary", String.format("Cân đối kiểm kê %s: %d món khớp, %d món lệch. Giá trị chênh lệch ròng: %s VND",
                                slipCode, matchedCount, discrepancyCount, netVarianceValue)
                )
        );

        return new StocktakeReconciliationDto(
                stocktake.getId(),
                slipCode,
                stocktake.getStatus().name(),
                now,
                stocktake.getLines().size(),
                matchedCount,
                discrepancyCount,
                netVarianceValue,
                details
        );
    }

    private void validateAdjustRequest(SubmitStocktakeCountsRequest request) {
        if (request == null || request.counts() == null || request.counts().isEmpty()) {
            throw ValidationException.single("counts", "Danh sách số đếm thực tế không được để trống.");
        }
        for (SubmitStocktakeCountsRequest.CountItem countItem : request.counts()) {
            if (countItem.ingredientId() == null) {
                throw ValidationException.single("ingredientId", "Mã nguyên vật liệu không được để trống.");
            }
            if (countItem.countedQuantity() == null || countItem.countedQuantity().compareTo(BigDecimal.ZERO) < 0) {
                throw ValidationException.single("countedQuantity", "Số lượng đếm thực tế không được âm.");
            }
        }
    }

    private static final DateTimeFormatter SLIP_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private String formatSlipCode(Stocktake stocktake) {
        Instant started = stocktake.getStartedAt() != null ? stocktake.getStartedAt() : Instant.now();
        String dateStr = started.atZone(ZoneOffset.UTC).format(SLIP_DATE_FORMATTER);
        Instant startOfDay = started.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        long seq = 1;
        if (stocktake.getId() != null) {
            seq = stocktakeRepository.countTodayBranchStocktakesUpToId(stocktake.getBranch().getId(), startOfDay, stocktake.getId());
        } else {
            seq = stocktakeRepository.countTodayBranchStocktakes(stocktake.getBranch().getId(), startOfDay) + 1;
        }
        return String.format("PKK-%s-%03d", dateStr, Math.max(1, seq));
    }

    private Integer resolveBranchIdForUser() {
        Integer branchId = BranchContext.current().map(BranchContext.Info::branchId).orElse(null);
        if (branchId != null) {
            return branchId;
        }
        User user = resolveCurrentUser();
        if (user.getBranchId() != null) {
            return user.getBranchId();
        }
        throw ValidationException.single("branchId", "Yêu cầu cung cấp mã chi nhánh.");
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

    private StocktakeSessionDto toSessionDto(Stocktake stocktake, String overrideRoundType, String overrideWitness) {
        return toSessionDto(stocktake, overrideRoundType, overrideWitness, false);
    }

    private StocktakeSessionDto toSessionDto(Stocktake stocktake, String overrideRoundType, String overrideWitness, boolean hideBookQuantity) {
        String slipCode = formatSlipCode(stocktake);
        String startedByName = stocktake.getStartedBy() != null
                ? (stocktake.getStartedBy().getFullName() != null && !stocktake.getStartedBy().getFullName().isBlank()
                ? stocktake.getStartedBy().getFullName()
                : stocktake.getStartedBy().getUsername())
                : "";

        List<StocktakeSessionDto.StocktakeLineDto> lineDtos = stocktake.getLines().stream().map(line -> {
            Ingredient ing = line.getIngredient();
            BigDecimal unitsPerPack = ing.getUnitsPerPack() != null && ing.getUnitsPerPack().compareTo(BigDecimal.ZERO) > 0
                    ? ing.getUnitsPerPack() : BigDecimal.ONE;

            BigDecimal bookQty = hideBookQuantity ? null : line.getBookQuantity();
            BigDecimal bookPackUnits = hideBookQuantity ? null : (line.getBookQuantity() != null
                    ? line.getBookQuantity().divide(unitsPerPack, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            BigDecimal variance = hideBookQuantity ? null : line.getVariance();
            String ingredientCode = String.format("ING-%03d", ing.getId());

            return new StocktakeSessionDto.StocktakeLineDto(
                    ing.getId(),
                    ingredientCode,
                    ing.getName(),
                    ing.getPackUnit(),
                    ing.getBaseUnit(),
                    bookQty,
                    bookPackUnits,
                    line.getCountedQuantity(),
                    variance
            );
        }).toList();

        return new StocktakeSessionDto(
                stocktake.getId(),
                slipCode,
                stocktake.getBranch().getId(),
                stocktake.getBranch().getName(),
                overrideRoundType != null ? overrideRoundType : "DAILY_CLOSING",
                stocktake.getStatus().name(),
                startedByName,
                overrideWitness,
                stocktake.getStartedAt(),
                lineDtos.size(),
                lineDtos
        );
    }
}
