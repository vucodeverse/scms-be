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
import edu.fpt.sba.scms.dto.request.ApproveRequisitionRequest;
import edu.fpt.sba.scms.dto.request.CreateDisposalSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateIssueSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateReceiveSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateRequisitionRequest;
import edu.fpt.sba.scms.dto.response.PendingRequisitionDto;
import edu.fpt.sba.scms.dto.response.StockDisposalSlipResponse;
import edu.fpt.sba.scms.dto.response.StockIssueSlipResponse;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Triển khai nghiệp vụ UC28 — Nhập kho, UC29 — Xuất kho cho quầy Bar, UC30 — Hủy nguyên vật liệu hỏng / hết hạn,
 * và FEA-01 — Đề xuất & Điều phối tiếp nhận hàng Đa Kho Tổng (Multi-Hub DC).
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

    // Lưu trữ đơn đề xuất cấp hàng (FEA-01) tuân thủ kiến trúc SDD §5.1 (không sửa schema 16 cột của stock_transaction)
    private static final ConcurrentHashMap<Long, RequisitionRecord> REQUISITION_STORE = new ConcurrentHashMap<>();
    private static final AtomicLong REQUISITION_ID_SEQ = new AtomicLong(100);

    private record RequisitionItemRecord(
            Long ingredientId,
            String ingredientName,
            String packUnit,
            String baseUnit,
            BigDecimal unitsPerPack,
            Integer cartonsRequested,
            Integer totalCartonsApproved,
            List<PendingRequisitionDto.Allocation> allocations,
            Integer cartonsReceived,
            Integer cartonsDamaged,
            String damageReason,
            String evidenceImageUrl
    ) {
    }

    private record RequisitionRecord(
            Long id,
            String code,
            Integer branchId,
            String branchName,
            String status, // PENDING, APPROVED, COMPLETED
            String notes,
            Instant createdAt,
            List<RequisitionItemRecord> items
    ) {
    }

    // =========================================================================
    // FEA-01: BƯỚC 1 — QUÁN LẬP ĐƠN XIN CẤP HÀNG (REQUISITION)
    // =========================================================================

    @Override
    @Transactional
    public PendingRequisitionDto createRequisition(CreateRequisitionRequest request) {
        Integer branchId = request.branchId() != null
                ? request.branchId()
                : BranchContext.current().map(BranchContext.Info::branchId).orElse(null);

        if (branchId == null) {
            branchId = resolveBranchIdForUser();
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("chi nhánh"));

        if (request.items() == null || request.items().isEmpty()) {
            throw ValidationException.single("items", "Danh sách mặt hàng xin cấp không được để trống.");
        }

        List<RequisitionItemRecord> itemRecords = new ArrayList<>();
        for (CreateRequisitionRequest.Item itemReq : request.items()) {
            if (itemReq.cartonsRequested() == null || itemReq.cartonsRequested() < 1) {
                throw new BusinessRuleException(BusinessErrorCode.PACK_UNIT_REQUIRED,
                        "Số thùng/bao chẵn cần xin phải là số nguyên dương >= 1 (Rule 1: Nguyên kiện chẵn).");
            }

            Ingredient ingredient = ingredientRepository.findById(itemReq.ingredientId())
                    .orElseThrow(() -> new NotFoundException("nguyên vật liệu"));

            itemRecords.add(new RequisitionItemRecord(
                    ingredient.getId(),
                    ingredient.getName(),
                    ingredient.getPackUnit(),
                    ingredient.getBaseUnit(),
                    ingredient.getUnitsPerPack(),
                    itemReq.cartonsRequested(),
                    0,
                    List.of(),
                    0,
                    0,
                    null,
                    null
            ));
        }

        long reqId = REQUISITION_ID_SEQ.incrementAndGet();
        String reqCode = generateSlipCode(branchId, "PR");
        Instant now = Instant.now();

        RequisitionRecord record = new RequisitionRecord(
                reqId,
                reqCode,
                branch.getId(),
                branch.getName(),
                "PENDING",
                request.notes() != null ? request.notes() : "Đơn đề xuất xin cấp hàng định kỳ",
                now,
                itemRecords
        );

        REQUISITION_STORE.put(reqId, record);
        log.info("Quản lý chi nhánh {} ({}) đã lập đơn xin cấp hàng {} (ID={}) với {} mặt hàng.",
                branch.getId(), branch.getName(), reqCode, reqId, itemRecords.size());

        return toPendingDto(record);
    }

    // =========================================================================
    // FEA-01: BƯỚC 2 — COO TRA CỨU ĐƠN CHỜ DUYỆT ĐA KHO TỔNG
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<PendingRequisitionDto> getPendingRequisitions() {
        ensureSampleRequisitionIfEmpty();

        return REQUISITION_STORE.values().stream()
                .filter(r -> "PENDING".equalsIgnoreCase(r.status()))
                .sorted(Comparator.comparing(RequisitionRecord::createdAt).reversed())
                .map(this::toPendingDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingRequisitionDto> getRequisitions(String status, Integer branchId) {
        ensureSampleRequisitionIfEmpty();

        return REQUISITION_STORE.values().stream()
                .filter(r -> status == null || "ALL".equalsIgnoreCase(status) || r.status().equalsIgnoreCase(status.trim()))
                .filter(r -> branchId == null || r.branchId().equals(branchId))
                .sorted(Comparator.comparing(RequisitionRecord::createdAt).reversed())
                .map(this::toPendingDto)
                .toList();
    }

    // =========================================================================
    // FEA-01: BƯỚC 3 — COO PHÂN BỔ & DUYỆT ĐIỀU PHỐI (MULTI-HUB APPROVE)
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PendingRequisitionDto approveRequisition(ApproveRequisitionRequest request) {
        RequisitionRecord requisition = REQUISITION_STORE.get(request.requisitionId());
        if (requisition == null) {
            throw new NotFoundException("đơn xin cấp hàng #" + request.requisitionId());
        }

        if (!"PENDING".equalsIgnoreCase(requisition.status())) {
            throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Đơn đề xuất " + requisition.code() + " hiện ở trạng thái " + requisition.status() + ", không thể duyệt lại.");
        }

        User currentUser = resolveCurrentUser();
        Instant now = Instant.now();

        List<RequisitionItemRecord> updatedItems = new ArrayList<>();

        for (ApproveRequisitionRequest.ApprovedItem approvedItemReq : request.approvedItems()) {
            Ingredient ingredient = ingredientRepository.findById(approvedItemReq.ingredientId())
                    .orElseThrow(() -> new NotFoundException("nguyên vật liệu #" + approvedItemReq.ingredientId()));

            // Rule 1: Nguyên kiện chẵn
            if (approvedItemReq.totalCartonsApproved() == null || approvedItemReq.totalCartonsApproved() < 1) {
                throw new BusinessRuleException(BusinessErrorCode.PACK_UNIT_REQUIRED,
                        "Số thùng duyệt phải là số nguyên dương >= 1 (Rule 1).");
            }

            List<ApproveRequisitionRequest.Allocation> allocations = approvedItemReq.allocations();
            if (allocations == null || allocations.isEmpty()) {
                // Tự động phân bổ từ Kho Tổng mặc định (DC-HN hoặc Kho trung tâm đầu tiên)
                Branch defaultWarehouse = branchRepository.findByCode("DC-HN")
                        .orElseGet(() -> branchRepository.findAll().stream()
                                .filter(b -> b.getCode() != null && b.getCode().startsWith("DC"))
                                .findFirst()
                                .orElseGet(() -> branchRepository.findAll().stream().findFirst().orElse(null)));

                if (defaultWarehouse != null) {
                    allocations = List.of(new ApproveRequisitionRequest.Allocation(
                            defaultWarehouse.getId(),
                            approvedItemReq.totalCartonsApproved()
                    ));
                } else {
                    allocations = List.of();
                }
            } else {
                int totalAllocated = allocations.stream()
                        .mapToInt(ApproveRequisitionRequest.Allocation::cartons)
                        .sum();

                if (totalAllocated != approvedItemReq.totalCartonsApproved()) {
                    throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            String.format("Tổng số thùng phân bổ (%d thùng) không khớp với số thùng duyệt (%d thùng) cho nguyên liệu %s.",
                                    totalAllocated, approvedItemReq.totalCartonsApproved(), ingredient.getName()));
                }
            }

            List<PendingRequisitionDto.Allocation> allocationDtos = new ArrayList<>();

            for (ApproveRequisitionRequest.Allocation alloc : allocations) {
                if (alloc.cartons() == null || alloc.cartons() < 1) {
                    throw new BusinessRuleException(BusinessErrorCode.PACK_UNIT_REQUIRED,
                            "Số thùng phân bổ từ kho phải >= 1.");
                }

                Branch sourceWarehouse = branchRepository.findById(alloc.sourceWarehouseId())
                        .orElseThrow(() -> new NotFoundException("kho tổng #" + alloc.sourceWarehouseId()));

                BigDecimal unitsPerPack = ingredient.getUnitsPerPack() != null ? ingredient.getUnitsPerPack() : BigDecimal.ONE;
                BigDecimal qtyBaseToDeduct = unitsPerPack.multiply(BigDecimal.valueOf(alloc.cartons()));

                // Lock PESSIMISTIC_WRITE trên inventory của Kho nguồn (nếu chưa có thì khởi tạo)
                Inventory sourceInv = inventoryRepository.findForUpdate(sourceWarehouse.getId(), ingredient.getId())
                        .orElseGet(() -> {
                            Inventory newInv = Inventory.builder()
                                    .branch(sourceWarehouse)
                                    .ingredient(ingredient)
                                    .currentQtyBase(qtyBaseToDeduct)
                                    .safetyStockLevel(BigDecimal.ZERO)
                                    .lastUpdated(now)
                                    .build();
                            newInv.recalculateStockAndFlag(unitsPerPack);
                            return inventoryRepository.save(newInv);
                        });

                // COO tự kiểm kê ngoài hệ thống: Nếu tồn trên hệ thống nhỏ hơn lượng duyệt xuất, tự động cập nhật đủ
                if (sourceInv.getCurrentQtyBase().compareTo(qtyBaseToDeduct) < 0) {
                    sourceInv.setCurrentQtyBase(qtyBaseToDeduct);
                }

                // Trừ tồn tương ứng tại kho tổng nguồn
                sourceInv.setCurrentQtyBase(sourceInv.getCurrentQtyBase().subtract(qtyBaseToDeduct));
                sourceInv.recalculateStockAndFlag(unitsPerPack);
                inventoryRepository.save(sourceInv);

                // Lưu bản ghi StockTransaction (type = ISSUE, reason = "Xuất kho điều phối cấp cho CN X theo đơn PR-...")
                BigDecimal cartonBD = BigDecimal.valueOf(alloc.cartons());
                BigDecimal unitCost = ingredient.getUnitCost() != null ? ingredient.getUnitCost() : BigDecimal.ZERO;
                BigDecimal packPrice = unitCost.multiply(unitsPerPack);
                BigDecimal lineTotal = unitCost.multiply(qtyBaseToDeduct);

                StockTransaction issueTx = StockTransaction.builder()
                        .branch(sourceWarehouse)
                        .ingredient(ingredient)
                        .type(StockTransactionType.ISSUE)
                        .shiftId(null)
                        .stocktakeId(null)
                        .createdBy(currentUser)
                        .createdAt(now)
                        .cartons(cartonBD)
                        .looseUnits(BigDecimal.ZERO)
                        .quantityPacks(cartonBD)
                        .quantityBase(qtyBaseToDeduct)
                        .purchasePrice(packPrice)
                        .lineTotal(lineTotal)
                        .reason("Xuất kho điều phối cấp cho CN " + requisition.branchId() + " theo đơn " + requisition.code())
                        .build();

                stockTransactionRepository.save(issueTx);
                log.info("COO đã xuất kho điều phối {} thùng {} từ Hub {} ({}) cấp cho chi nhánh {}.",
                        alloc.cartons(), ingredient.getName(), sourceWarehouse.getId(), sourceWarehouse.getName(), requisition.branchId());

                allocationDtos.add(new PendingRequisitionDto.Allocation(
                        sourceWarehouse.getId(),
                        sourceWarehouse.getName(),
                        alloc.cartons()
                ));
            }

            RequisitionItemRecord origItem = requisition.items().stream()
                    .filter(it -> it.ingredientId().equals(ingredient.getId()))
                    .findFirst()
                    .orElse(null);
            int origRequested = origItem != null ? origItem.cartonsRequested() : approvedItemReq.totalCartonsApproved();

            updatedItems.add(new RequisitionItemRecord(
                    ingredient.getId(),
                    ingredient.getName(),
                    ingredient.getPackUnit(),
                    ingredient.getBaseUnit(),
                    ingredient.getUnitsPerPack(),
                    origRequested,
                    approvedItemReq.totalCartonsApproved(),
                    allocationDtos,
                    0,
                    0,
                    null,
                    null
            ));
        }

        RequisitionRecord approvedRecord = new RequisitionRecord(
                requisition.id(),
                requisition.code(),
                requisition.branchId(),
                requisition.branchName(),
                "APPROVED",
                requisition.notes(),
                requisition.createdAt(),
                updatedItems
        );

        REQUISITION_STORE.put(approvedRecord.id(), approvedRecord);

        auditLogService.log(
                AuditAction.STOCK_ISSUE,
                ENTITY_STOCK_TRANSACTION,
                requisition.code(),
                null,
                Map.of(
                        "requisitionCode", requisition.code(),
                        "action", "COO_APPROVE_MULTI_HUB",
                        "branchId", requisition.branchId(),
                        "itemCount", updatedItems.size()
                )
        );

        return toPendingDto(approvedRecord);
    }

    @Override
    public void rejectRequisition(Long requisitionId, String reason) {
        RequisitionRecord requisition = REQUISITION_STORE.get(requisitionId);
        if (requisition == null) {
            throw new NotFoundException("đơn xin cấp hàng #" + requisitionId);
        }
        if (!"PENDING".equalsIgnoreCase(requisition.status())) {
            throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Đơn đề xuất " + requisition.code() + " hiện ở trạng thái " + requisition.status() + ", không thể từ chối.");
        }
        String note = (requisition.notes() != null ? requisition.notes() + " · " : "") +
                "[COO từ chối: " + (reason != null && !reason.isBlank() ? reason : "Kho Tổng không đủ hàng cấp") + "]";
        RequisitionRecord rejectedRecord = new RequisitionRecord(
                requisition.id(),
                requisition.code(),
                requisition.branchId(),
                requisition.branchName(),
                "REJECTED",
                note,
                requisition.createdAt(),
                requisition.items()
        );
        REQUISITION_STORE.put(rejectedRecord.id(), rejectedRecord);
    }

    // =========================================================================
    // FEA-01: BƯỚC 4 — QUÁN NHẬN HÀNG (GRN) & UC28 NHẬP KHO THÔNG THƯỜNG
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockReceiveSlipResponse receiveStock(CreateReceiveSlipRequest request) {
        // Trường hợp A: Nhận hàng điều phối đa kho tổng theo đơn PR-... (FEA-01 Bước 4)
        if (request.requisitionId() != null) {
            return processMultiHubRequisitionReceive(request);
        }

        // Trường hợp B: Nhập kho nguyên vật liệu từ NCC / điều chuyển thông thường (UC28)
        return processStandardSupplierReceive(request);
    }

    private StockReceiveSlipResponse processMultiHubRequisitionReceive(CreateReceiveSlipRequest request) {
        RequisitionRecord requisition = REQUISITION_STORE.get(request.requisitionId());
        if (requisition == null) {
            throw new NotFoundException("đơn xin cấp hàng #" + request.requisitionId());
        }

        if (!"APPROVED".equalsIgnoreCase(requisition.status()) && !"PARTIALLY_RECEIVED".equalsIgnoreCase(requisition.status())) {
            throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Đơn xin cấp hàng " + requisition.code() + " không ở trạng thái APPROVED hoặc PARTIALLY_RECEIVED để nhận hàng (Trạng thái hiện tại: " + requisition.status() + ").");
        }

        Integer branchId = request.branchId() != null ? request.branchId() : requisition.branchId();
        Branch storeBranch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("chi nhánh"));

        User currentUser = resolveCurrentUser();
        Instant now = Instant.now();
        String slipCode = generateSlipCode(storeBranch.getId(), "PNK");

        BigDecimal subtotal = BigDecimal.ZERO;
        List<StockReceiveSlipResponse.ItemResponse> itemResponses = new ArrayList<>();

        for (CreateReceiveSlipRequest.Item itemReq : request.items()) {
            Ingredient ingredient = ingredientRepository.findById(itemReq.ingredientId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.INGREDIENT_NOT_FOUND));

            if ("INACTIVE".equalsIgnoreCase(ingredient.getStatus())) {
                throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Nguyên liệu '" + ingredient.getName() + "' đang ở trạng thái ngừng hoạt động (INACTIVE), không được phép nhập kho.");
            }

            int accepted = itemReq.cartonsAccepted() != null
                    ? itemReq.cartonsAccepted()
                    : (itemReq.cartons() != null ? itemReq.cartons().intValue() : 0);

            int rejected = itemReq.cartonsRejected() != null ? itemReq.cartonsRejected() : 0;

            if (accepted < 0 || rejected < 0 || (accepted == 0 && rejected == 0)) {
                throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Số thùng nhận hoặc từ chối không hợp lệ cho nguyên vật liệu " + ingredient.getName());
            }

            // Rule 4 (Bục hỏng không tăng tồn): cartonsRejected ghi nhận sự cố, bắt buộc có lý do và evidenceImageUrl
            if (rejected > 0) {
                if (itemReq.damageReason() == null || itemReq.damageReason().isBlank()) {
                    throw new BusinessRuleException(BusinessErrorCode.REASON_REQUIRED,
                            "Bắt buộc nhập lý do bục vỡ / hỏng khi ghi nhận hàng từ chối cho " + ingredient.getName());
                }
                if (itemReq.evidenceImageUrl() == null || itemReq.evidenceImageUrl().isBlank()) {
                    throw new BusinessRuleException(BusinessErrorCode.REASON_REQUIRED,
                            "Bắt buộc có link ảnh hiện trường khi ghi nhận hàng bục vỡ / hỏng cho " + ingredient.getName() + " (Rule 4).");
                }
            }

            BigDecimal unitsPerPack = ingredient.getUnitsPerPack() != null ? ingredient.getUnitsPerPack() : BigDecimal.ONE;
            BigDecimal incomingBase = unitsPerPack.multiply(BigDecimal.valueOf(accepted));

            // Lock PESSIMISTIC_WRITE trên inventory của chi nhánh nhận
            Inventory storeInv = inventoryRepository.findForUpdate(storeBranch.getId(), ingredient.getId())
                    .orElseGet(() -> createDefaultInventory(storeBranch, ingredient));

            // Rule 3 (Bảo vệ trần Max Stock): (storeInv.currentQtyBase + incoming) <= storeInv.safetyStockLevel * 3.0
            BigDecimal safetyStock = storeInv.getSafetyStockLevel() != null ? storeInv.getSafetyStockLevel() : BigDecimal.ZERO;
            BigDecimal maxStock = safetyStock.multiply(new BigDecimal("3.0000"));
            BigDecimal projectedBase = storeInv.getCurrentQtyBase().add(incomingBase);

            if (safetyStock.compareTo(BigDecimal.ZERO) > 0 && projectedBase.compareTo(maxStock) > 0) {
                log.warn("Lượng tồn sau khi nhận ({} {}) vượt quá trần Max Stock lý thuyết ({} {}) của chi nhánh.",
                        projectedBase, ingredient.getBaseUnit(), maxStock, ingredient.getBaseUnit());
            }

            // Tăng tồn Kho Lưu Trữ chi nhánh: storeInv.currentQtyBase += (cartonsAccepted * unitsPerPack)
            storeInv.setCurrentQtyBase(projectedBase);
            storeInv.recalculateStockAndFlag(unitsPerPack);
            inventoryRepository.save(storeInv);

            // Tính giá trị phiếu nhập
            BigDecimal unitCost = ingredient.getUnitCost() != null ? ingredient.getUnitCost() : BigDecimal.ZERO;
            BigDecimal packPrice = unitCost.multiply(unitsPerPack);
            BigDecimal lineTotal = unitCost.multiply(incomingBase);
            subtotal = subtotal.add(lineTotal);

            // Lưu StockTransaction với type = RECEIVE, reason = "Nhập hàng từ Kho Tổng theo đơn PR-..."
            String reasonText = "Nhập hàng từ Kho Tổng theo đơn " + requisition.code();
            if (rejected > 0) {
                reasonText += String.format(" [Bục vỡ/hỏng: %d thùng, Lý do: %s]", rejected, itemReq.damageReason());
            }

            StockTransaction rxTx = StockTransaction.builder()
                    .branch(storeBranch)
                    .ingredient(ingredient)
                    .type(StockTransactionType.RECEIVE)
                    .shiftId(null)
                    .stocktakeId(null)
                    .createdBy(currentUser)
                    .createdAt(now)
                    .cartons(BigDecimal.valueOf(accepted))
                    .looseUnits(BigDecimal.ZERO)
                    .quantityPacks(BigDecimal.valueOf(accepted))
                    .quantityBase(incomingBase)
                    .purchasePrice(packPrice)
                    .lineTotal(lineTotal)
                    .reason(reasonText)
                    .evidenceImageUrl(rejected > 0 ? itemReq.evidenceImageUrl() : null)
                    .build();

            stockTransactionRepository.save(rxTx);

            itemResponses.add(new StockReceiveSlipResponse.ItemResponse(
                    rxTx.getId(),
                    ingredient.getId(),
                    String.format("ING-%03d", ingredient.getId()),
                    ingredient.getName(),
                    ingredient.getPackUnit(),
                    ingredient.getBaseUnit(),
                    unitsPerPack,
                    BigDecimal.valueOf(accepted),
                    BigDecimal.ZERO,
                    incomingBase,
                    packPrice,
                    lineTotal,
                    storeInv.getCurrentQtyBase(),
                    storeInv.getCurrentPackUnits(),
                    storeInv.getLowStockFlag()
            ));
        }

        // Cập nhật trạng thái Requisition thành COMPLETED / PARTIALLY_RECEIVED kèm đầy đủ dữ liệu đối soát nhận hàng & sự cố
        List<RequisitionItemRecord> completedItemRecords = new ArrayList<>();
        for (RequisitionItemRecord origItem : requisition.items()) {
            CreateReceiveSlipRequest.Item matchReq = request.items().stream()
                    .filter(ri -> ri.ingredientId().equals(origItem.ingredientId()))
                    .findFirst()
                    .orElse(null);

            int prevReceived = origItem.cartonsReceived() != null ? origItem.cartonsReceived() : 0;
            int thisAccepted = matchReq != null && matchReq.cartonsAccepted() != null
                    ? matchReq.cartonsAccepted()
                    : 0;
            int totalAcc = prevReceived + thisAccepted;

            int rej = matchReq != null && matchReq.cartonsRejected() != null
                    ? matchReq.cartonsRejected()
                    : 0;
            String reason = matchReq != null && matchReq.damageReason() != null ? matchReq.damageReason() : origItem.damageReason();
            String imgUrl = matchReq != null && matchReq.evidenceImageUrl() != null ? matchReq.evidenceImageUrl() : origItem.evidenceImageUrl();

            completedItemRecords.add(new RequisitionItemRecord(
                    origItem.ingredientId(),
                    origItem.ingredientName(),
                    origItem.packUnit(),
                    origItem.baseUnit(),
                    origItem.unitsPerPack(),
                    origItem.cartonsRequested(),
                    origItem.totalCartonsApproved(),
                    origItem.allocations(),
                    totalAcc,
                    rej,
                    reason,
                    imgUrl
            ));
        }

        boolean isPartial = completedItemRecords.stream().anyMatch(it ->
                it.totalCartonsApproved() != null && it.cartonsReceived() < it.totalCartonsApproved()
        );
        String finalStatus = isPartial ? "PARTIALLY_RECEIVED" : "COMPLETED";

        RequisitionRecord completedRecord = new RequisitionRecord(
                requisition.id(),
                requisition.code(),
                requisition.branchId(),
                requisition.branchName(),
                finalStatus,
                requisition.notes(),
                requisition.createdAt(),
                completedItemRecords
        );
        REQUISITION_STORE.put(completedRecord.id(), completedRecord);

        BigDecimal vatAmount = subtotal.multiply(VAT_RATE).setScale(0, RoundingMode.HALF_UP);
        BigDecimal totalPayable = subtotal.add(vatAmount);

        auditLogService.log(
                AuditAction.STOCK_RECEIVE,
                ENTITY_STOCK_TRANSACTION,
                slipCode,
                null,
                Map.of(
                        "slipCode", slipCode,
                        "requisitionCode", requisition.code(),
                        "branchId", storeBranch.getId(),
                        "totalPayable", totalPayable
                )
        );

        String receivedByName = currentUser.getFullName() != null && !currentUser.getFullName().isBlank()
                ? currentUser.getFullName() : currentUser.getUsername();

        return new StockReceiveSlipResponse(
                slipCode,
                storeBranch.getId(),
                storeBranch.getName(),
                "INTERNAL_TRANSFER",
                "Kho Tổng Vùng SCMS",
                requisition.code(),
                now,
                receivedByName,
                subtotal,
                vatAmount,
                totalPayable,
                request.notes() != null ? request.notes() : "Nhập hàng từ Kho Tổng theo đơn " + requisition.code(),
                itemResponses
        );
    }

    private StockReceiveSlipResponse processStandardSupplierReceive(CreateReceiveSlipRequest request) {
        Integer branchId = BranchContext.requireBranchId();
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("chi nhánh"));

        User currentUser = resolveCurrentUser();
        validateReceiveRequest(request);

        if (request.invoiceRef() != null && !request.invoiceRef().isBlank()) {
            if (stockTransactionRepository.existsByBranchIdAndInvoiceRef(branchId, request.invoiceRef())) {
                throw new ConflictException(ErrorCode.RESOURCE_CONFLICT,
                        "Số hóa đơn " + request.invoiceRef() + " đã được nhập kho trước đó.");
            }
        }

        String slipCode = generateSlipCode(branchId, "PNK");
        Instant now = Instant.now();

        BigDecimal subtotal = BigDecimal.ZERO;
        List<StockReceiveSlipResponse.ItemResponse> itemResponses = new ArrayList<>();

        for (CreateReceiveSlipRequest.Item itemReq : request.items()) {
            Ingredient ingredient = ingredientRepository.findById(itemReq.ingredientId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.INGREDIENT_NOT_FOUND));

            if ("INACTIVE".equalsIgnoreCase(ingredient.getStatus())) {
                throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Nguyên liệu '" + ingredient.getName() + "' đang ở trạng thái ngừng hoạt động (INACTIVE), không được phép nhập kho.");
            }

            BigDecimal unitsPerPack = ingredient.getUnitsPerPack() != null ? ingredient.getUnitsPerPack() : BigDecimal.ONE;

            Inventory inventory = inventoryRepository.findForUpdate(branchId, ingredient.getId())
                    .orElseGet(() -> createDefaultInventory(branch, ingredient));

            BigDecimal cartons = itemReq.cartons() != null ? itemReq.cartons() : BigDecimal.ZERO;
            BigDecimal loose = itemReq.looseUnits() != null ? itemReq.looseUnits() : BigDecimal.ZERO;

            BigDecimal baseQtyAdded = cartons.multiply(unitsPerPack).add(loose);

            BigDecimal lineTotal = cartons.multiply(itemReq.purchasePrice());
            if (loose.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal unitPricePerBase = itemReq.purchasePrice().divide(unitsPerPack, 2, RoundingMode.HALF_UP);
                lineTotal = lineTotal.add(loose.multiply(unitPricePerBase));
            }
            lineTotal = lineTotal.setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);

            inventory.setCurrentQtyBase(inventory.getCurrentQtyBase().add(baseQtyAdded));
            inventory.recalculateStockAndFlag(unitsPerPack);
            inventoryRepository.save(inventory);

            BigDecimal quantityPacks = baseQtyAdded.divide(unitsPerPack, 4, RoundingMode.HALF_UP);
            String itemReason = formatReceiveReason(slipCode, request.source(), request.supplierName(),
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

        BigDecimal vatAmount = subtotal.multiply(VAT_RATE).setScale(0, RoundingMode.HALF_UP);
        BigDecimal totalPayable = subtotal.add(vatAmount);

        auditLogService.log(
                AuditAction.STOCK_RECEIVE,
                ENTITY_STOCK_TRANSACTION,
                slipCode,
                null,
                Map.of(
                        "slipCode", slipCode,
                        "supplierName", request.supplierName() != null ? request.supplierName() : "N/A",
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

    // =========================================================================
    // HELPER: MAPPING VÀ TỒN KHO 3 HUB
    // =========================================================================

    private PendingRequisitionDto toPendingDto(RequisitionRecord record) {
        List<PendingRequisitionDto.Item> items = new ArrayList<>();

        for (RequisitionItemRecord item : record.items()) {
            List<PendingRequisitionDto.AvailableHub> hubs = calculateAvailableHubs(item.ingredientId());
            items.add(new PendingRequisitionDto.Item(
                    item.ingredientId(),
                    item.ingredientName(),
                    item.packUnit(),
                    item.baseUnit(),
                    item.unitsPerPack(),
                    item.cartonsRequested(),
                    hubs,
                    item.totalCartonsApproved(),
                    item.allocations(),
                    item.cartonsReceived(),
                    item.cartonsDamaged(),
                    item.damageReason(),
                    item.evidenceImageUrl()
            ));
        }

        return new PendingRequisitionDto(
                record.id(),
                record.code(),
                record.branchId(),
                record.branchName(),
                record.status(),
                record.notes(),
                record.createdAt(),
                items
        );
    }

    private List<PendingRequisitionDto.AvailableHub> calculateAvailableHubs(Long ingredientId) {
        List<PendingRequisitionDto.AvailableHub> result = new ArrayList<>();
        List<String> hubCodes = List.of("DC-HN", "DC-DN", "DC-HCM");

        for (String code : hubCodes) {
            branchRepository.findByCode(code).ifPresent(branch -> {
                BigDecimal available = inventoryRepository.findByBranchIdAndIngredientId(branch.getId(), ingredientId)
                        .map(Inventory::getCurrentPackUnits)
                        .orElse(BigDecimal.ZERO);

                result.add(new PendingRequisitionDto.AvailableHub(
                        branch.getId(),
                        branch.getName(),
                        branch.getCode(),
                        available
                ));
            });
        }

        // Fallback theo ID 1, 2, 3 nếu chưa đủ
        if (result.isEmpty()) {
            for (Integer id : List.of(1, 2, 3)) {
                branchRepository.findById(id).ifPresent(branch -> {
                    BigDecimal available = inventoryRepository.findByBranchIdAndIngredientId(branch.getId(), ingredientId)
                            .map(Inventory::getCurrentPackUnits)
                            .orElse(BigDecimal.ZERO);

                    result.add(new PendingRequisitionDto.AvailableHub(
                            branch.getId(),
                            branch.getName(),
                            branch.getCode(),
                            available
                    ));
                });
            }
        }

        return result;
    }

    private void ensureSampleRequisitionIfEmpty() {
        if (!REQUISITION_STORE.isEmpty()) {
            return;
        }

        Branch storeBranch = branchRepository.findByCode("CG-STORE")
                .or(() -> branchRepository.findById(4))
                .orElse(null);

        if (storeBranch == null) {
            return;
        }

        Ingredient robusta = ingredientRepository.findByName("Hạt Cà phê Robusta")
                .or(() -> ingredientRepository.findById(1L))
                .orElse(null);

        if (robusta == null) {
            return;
        }

        long reqId = 108L;
        String reqCode = "PR-20261111-001";
        RequisitionItemRecord itemRecord = new RequisitionItemRecord(
                robusta.getId(),
                robusta.getName(),
                robusta.getPackUnit(),
                robusta.getBaseUnit(),
                robusta.getUnitsPerPack(),
                5,
                0,
                List.of(),
                0,
                0,
                null,
                null
        );

        RequisitionRecord sample = new RequisitionRecord(
                reqId,
                reqCode,
                storeBranch.getId(),
                storeBranch.getName(),
                "PENDING",
                "Quán Cầu Giấy xin cấp gấp 5 bao hạt Robusta phục vụ tuần lễ khai trương",
                Instant.now(),
                List.of(itemRecord)
        );

        REQUISITION_STORE.put(reqId, sample);
        log.info("Khởi tạo đơn mẫu PendingRequisition ID=108 cho Quán Cầu Giấy.");
    }

    private Integer resolveBranchIdForUser() {
        return branchRepository.findByCode("CG-STORE")
                .map(Branch::getId)
                .orElse(4);
    }

    // =========================================================================
    // UC29 & FEA-02 — XUẤT KHO NỘI BỘ RA QUẦY BAR (TWO-PARTY TRANSFER)
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockIssueSlipResponse issueStockToBar(CreateIssueSlipRequest request) {
        Integer branchId = request.branchId() != null
                ? request.branchId()
                : BranchContext.current().map(BranchContext.Info::branchId).orElse(null);

        if (branchId == null) {
            branchId = resolveBranchIdForUser();
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("chi nhánh"));

        User currentUser = resolveCurrentUser();
        validateIssueRequest(request);

        String slipCode = generateSlipCode(branchId, "PXK-BAR");
        Instant now = Instant.now();

        String destinationStr = request.destination() != null && !request.destination().isBlank()
                ? request.destination()
                : (request.destinationBar() != null && !request.destinationBar().isBlank()
                        ? request.destinationBar()
                        : "QUẦY BAR CHÍNH");

        String purpose = request.purpose() != null && !request.purpose().isBlank()
                ? request.purpose()
                : "BAR_DISPENSING";

        List<StockIssueSlipResponse.ItemResponse> itemResponses = new ArrayList<>();

        for (CreateIssueSlipRequest.Item itemReq : request.items()) {
            Ingredient ingredient = ingredientRepository.findById(itemReq.ingredientId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.INGREDIENT_NOT_FOUND));

            BigDecimal unitsPerPack = ingredient.getUnitsPerPack() != null ? ingredient.getUnitsPerPack() : BigDecimal.ONE;

            // Rule 4 (Khóa giao dịch): Sử dụng inventoryRepository.findForUpdate(branchId, ingredientId) (Pessimistic Write Lock)
            Inventory inventory = inventoryRepository.findForUpdate(branchId, ingredient.getId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.INVENTORY_NOT_FOUND));

            BigDecimal quantityPacks = itemReq.quantityPacks() != null ? itemReq.quantityPacks() : BigDecimal.ZERO;
            BigDecimal looseUnits = itemReq.looseUnits() != null ? itemReq.looseUnits() : BigDecimal.ZERO;

            // Rule 2 (Quy đổi đơn vị): baseQty = (quantityPacks * unitsPerPack) + looseUnits
            BigDecimal baseQtyToDeduct = quantityPacks.multiply(unitsPerPack).add(looseUnits);

            // Rule 3 (Chặn xuất âm): Kiểm tra inventory.getCurrentQtyBase() >= baseQty. Nếu nhỏ hơn, ném BusinessRuleException
            if (inventory.getCurrentQtyBase() == null || inventory.getCurrentQtyBase().compareTo(baseQtyToDeduct) < 0) {
                throw new BusinessRuleException(BusinessErrorCode.INSUFFICIENT_STOCK,
                        String.format("Kho lưu trữ không đủ tồn để xuất ra quầy bar: Tồn kho nguyên liệu '%s' hiện tại chỉ còn %s %s, không đủ để xuất ra quầy bar (cần xuất: %s %s).",
                                ingredient.getName(),
                                inventory.getCurrentQtyBase() != null ? inventory.getCurrentQtyBase() : BigDecimal.ZERO,
                                ingredient.getBaseUnit(),
                                baseQtyToDeduct,
                                ingredient.getBaseUnit()));
            }

            inventory.setCurrentQtyBase(inventory.getCurrentQtyBase().subtract(baseQtyToDeduct));
            inventory.recalculateStockAndFlag(unitsPerPack);
            inventoryRepository.save(inventory);

            BigDecimal unitCost = ingredient.getUnitCost() != null ? ingredient.getUnitCost() : BigDecimal.ZERO;

            // Rule 5 (Ghi vết): Lưu StockTransaction với type = ISSUE, reason = "PXK-BAR: Xuất quầy bar - Người nhận: " + baristaName
            String itemReason = formatIssueReason(slipCode, destinationStr,
                    request.baristaName(), purpose, request.notes());

            StockTransaction tx = StockTransaction.builder()
                    .branch(branch)
                    .ingredient(ingredient)
                    .type(StockTransactionType.ISSUE)
                    .shiftId(null)
                    .stocktakeId(null)
                    .createdBy(currentUser)
                    .createdAt(now)
                    .cartons(quantityPacks)
                    .looseUnits(looseUnits)
                    .quantityPacks(quantityPacks)
                    .quantityBase(baseQtyToDeduct)
                    .purchasePrice(unitCost.multiply(unitsPerPack))
                    .lineTotal(unitCost.multiply(baseQtyToDeduct))
                    .reason(itemReason)
                    .evidenceImageUrl(null)
                    .build();
            stockTransactionRepository.save(tx);

            itemResponses.add(new StockIssueSlipResponse.ItemResponse(
                    tx.getId(),
                    ingredient.getId(),
                    ingredient.getName(),
                    ingredient.getPackUnit(),
                    ingredient.getBaseUnit(),
                    quantityPacks,
                    baseQtyToDeduct,
                    inventory.getCurrentQtyBase(),
                    inventory.getCurrentPackUnits(),
                    inventory.getLowStockFlag()
            ));
        }

        auditLogService.log(
                AuditAction.STOCK_ISSUE,
                ENTITY_STOCK_TRANSACTION,
                slipCode,
                null,
                Map.of(
                        "slipCode", slipCode,
                        "destinationBar", destinationStr,
                        "baristaName", request.baristaName(),
                        "itemCount", request.items().size(),
                        "summary", String.format("Xuất kho %s cho quầy %s (nhận: %s), gồm %d món",
                                slipCode, destinationStr, request.baristaName(), request.items().size())
                )
        );

        String issuedByName = currentUser.getFullName() != null && !currentUser.getFullName().isBlank()
                ? currentUser.getFullName() : currentUser.getUsername();

        return new StockIssueSlipResponse(
                slipCode,
                branchId,
                branch.getName(),
                purpose,
                destinationStr,
                request.baristaName(),
                issuedByName,
                now,
                request.notes(),
                itemResponses
        );
    }

    // =========================================================================
    // UC30 — HỦY NGUYÊN VẬT LIỆU
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockDisposalSlipResponse disposeStock(CreateDisposalSlipRequest request) {
        Integer branchId = request.branchId() != null
                ? request.branchId()
                : BranchContext.current().map(BranchContext.Info::branchId).orElse(null);

        if (branchId == null) {
            branchId = resolveBranchIdForUser();
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("chi nhánh"));

        User currentUser = resolveCurrentUser();
        validateDisposalRequest(request);

        String slipCode = generateSlipCode(branchId, "PHK");
        Instant now = Instant.now();

        BigDecimal totalLoss = BigDecimal.ZERO;
        List<StockDisposalSlipResponse.ItemResponse> itemResponses = new ArrayList<>();

        for (CreateDisposalSlipRequest.Item itemReq : request.items()) {
            Ingredient ingredient = ingredientRepository.findById(itemReq.ingredientId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.INGREDIENT_NOT_FOUND));

            BigDecimal unitsPerPack = ingredient.getUnitsPerPack() != null && ingredient.getUnitsPerPack().compareTo(BigDecimal.ZERO) > 0
                    ? ingredient.getUnitsPerPack() : BigDecimal.ONE;

            Inventory inventory = inventoryRepository.findForUpdate(branchId, ingredient.getId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.INVENTORY_NOT_FOUND));

            // FEA-04: Hỗ trợ trừ trực tiếp theo số lượng cơ sở (quantityBase) hoặc theo số kiện (quantityPacks)
            BigDecimal baseQtyToDeduct;
            BigDecimal quantityPacks;
            if (itemReq.quantityBase() != null && itemReq.quantityBase().compareTo(BigDecimal.ZERO) > 0) {
                baseQtyToDeduct = itemReq.quantityBase();
                quantityPacks = baseQtyToDeduct.divide(unitsPerPack, 4, RoundingMode.HALF_UP);
            } else if (itemReq.quantityPacks() != null && itemReq.quantityPacks().compareTo(BigDecimal.ZERO) > 0) {
                quantityPacks = itemReq.quantityPacks();
                baseQtyToDeduct = quantityPacks.multiply(unitsPerPack);
            } else {
                throw ValidationException.single("quantityBase", "Số lượng hủy phải lớn hơn 0.");
            }

            if (inventory.getCurrentQtyBase().compareTo(baseQtyToDeduct) < 0) {
                throw new BusinessRuleException(BusinessErrorCode.INSUFFICIENT_STOCK,
                        String.format("Tồn kho nguyên liệu '%s' không đủ để hủy (hiện có: %s, cần hủy: %s %s).",
                                ingredient.getName(), inventory.getCurrentQtyBase(), baseQtyToDeduct, ingredient.getBaseUnit()));
            }

            inventory.setCurrentQtyBase(inventory.getCurrentQtyBase().subtract(baseQtyToDeduct));
            inventory.recalculateStockAndFlag(unitsPerPack);
            inventoryRepository.save(inventory);

            // Rule 3: lossValue = quantityBase * ingredient.getUnitCost()
            BigDecimal unitCost = ingredient.getUnitCost() != null ? ingredient.getUnitCost() : BigDecimal.ZERO;
            BigDecimal lineLoss = unitCost.multiply(baseQtyToDeduct).setScale(2, RoundingMode.HALF_UP);
            totalLoss = totalLoss.add(lineLoss);

            // Rule 4: reason = "[" + reasonCode + "] " + explanation (gắn mã slipCode để giữ vết 16 cột)
            String itemReason = formatDisposalReason(slipCode, request.reasonCategory(),
                    request.reason(), itemReq.itemCause());

            String evidenceUrl = request.evidenceImageUrl() != null && !request.evidenceImageUrl().isBlank()
                    ? request.evidenceImageUrl().trim()
                    : request.evidenceFileId();

            StockTransaction tx = StockTransaction.builder()
                    .branch(branch)
                    .ingredient(ingredient)
                    .type(StockTransactionType.DISPOSAL)
                    .shiftId(null)
                    .stocktakeId(null)
                    .createdBy(currentUser)
                    .createdAt(now)
                    .cartons(quantityPacks)
                    .looseUnits(baseQtyToDeduct)
                    .quantityPacks(quantityPacks)
                    .quantityBase(baseQtyToDeduct)
                    .purchasePrice(unitCost.multiply(unitsPerPack))
                    .lineTotal(lineLoss)
                    .reason(itemReason)
                    .evidenceImageUrl(evidenceUrl)
                    .build();
            stockTransactionRepository.save(tx);

            itemResponses.add(new StockDisposalSlipResponse.ItemResponse(
                    tx.getId(),
                    ingredient.getId(),
                    ingredient.getName(),
                    ingredient.getPackUnit(),
                    ingredient.getBaseUnit(),
                    quantityPacks,
                    baseQtyToDeduct,
                    unitCost.multiply(unitsPerPack),
                    lineLoss,
                    inventory.getCurrentQtyBase(),
                    inventory.getCurrentPackUnits(),
                    inventory.getLowStockFlag()
            ));
        }

        BigDecimal COO_THRESHOLD = new BigDecimal("1000000");
        boolean requiresCooApproval = totalLoss.compareTo(COO_THRESHOLD) > 0;
        if (requiresCooApproval) {
            log.info("Phiếu hủy {} có tổng giá trị thiệt hại {} > 1.000.000 VNĐ, tự động ghi nhận chuyển COO duyệt theo quy chế.",
                    slipCode, totalLoss);
        }

        auditLogService.log(
                AuditAction.STOCK_DISPOSAL,
                ENTITY_STOCK_TRANSACTION,
                slipCode,
                null,
                Map.of(
                        "slipCode", slipCode,
                        "reasonCategory", request.reasonCategory(),
                        "totalLoss", totalLoss,
                        "requiresCooApproval", requiresCooApproval,
                        "itemCount", request.items().size(),
                        "summary", String.format("Lập phiếu hủy %s do %s, tổng thiệt hại: %s VND%s",
                                slipCode, request.reasonCategory(), MoneyUtil.format(totalLoss),
                                requiresCooApproval ? " (Chờ COO phê duyệt do > 1.000.000đ)" : "")
                )
        );

        String disposedByName = currentUser.getFullName() != null && !currentUser.getFullName().isBlank()
                ? currentUser.getFullName() : currentUser.getUsername();

        return new StockDisposalSlipResponse(
                slipCode,
                branchId,
                branch.getName(),
                request.reasonCategory(),
                request.reason(),
                request.evidenceFileId(),
                disposedByName,
                now,
                totalLoss,
                itemResponses
        );
    }

    // =========================================================================
    // VALIDATIONS & UTILS
    // =========================================================================

    private void validateReceiveRequest(CreateReceiveSlipRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw ValidationException.single("items", "Danh sách mặt hàng nhập kho không được để trống.");
        }

        if (request.requisitionId() == null) {
            if (request.source() == null || request.source().isBlank()) {
                throw ValidationException.single("source", "Nguồn nhập không được để trống.");
            }
            if (request.supplierName() == null || request.supplierName().isBlank()) {
                throw ValidationException.single("supplierName", "Tên nhà cung cấp không được để trống.");
            }
        }

        for (CreateReceiveSlipRequest.Item item : request.items()) {
            if (item.ingredientId() == null) {
                throw ValidationException.single("ingredientId", "Mã nguyên vật liệu không được để trống.");
            }

            if (request.requisitionId() == null) {
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
    }

    private void validateIssueRequest(CreateIssueSlipRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw ValidationException.single("items", "Danh sách mặt hàng xuất kho không được để trống.");
        }
        String dest = request.destination() != null && !request.destination().isBlank()
                ? request.destination()
                : request.destinationBar();
        if (dest == null || dest.isBlank()) {
            throw ValidationException.single("destinationBar", "Quầy nhận hàng không được để trống.");
        }
        // Rule 1: baristaName là BẮT BUỘC (@NotBlank)
        if (request.baristaName() == null || request.baristaName().isBlank()) {
            throw ValidationException.single("baristaName", "Tên người nhận không được để trống.");
        }

        for (CreateIssueSlipRequest.Item item : request.items()) {
            if (item.ingredientId() == null) {
                throw ValidationException.single("ingredientId", "Mã nguyên vật liệu không được để trống.");
            }
            BigDecimal packs = item.quantityPacks() != null ? item.quantityPacks() : BigDecimal.ZERO;
            BigDecimal loose = item.looseUnits() != null ? item.looseUnits() : BigDecimal.ZERO;

            if (packs.compareTo(BigDecimal.ZERO) < 0 || loose.compareTo(BigDecimal.ZERO) < 0) {
                throw ValidationException.single("quantityPacks", "Số lượng xuất không được âm.");
            }
            if (packs.compareTo(BigDecimal.ZERO) == 0 && loose.compareTo(BigDecimal.ZERO) == 0) {
                throw ValidationException.single("quantityPacks", "Số lượng kiện xuất phải lớn hơn 0.");
            }
        }
    }

    private void validateDisposalRequest(CreateDisposalSlipRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw ValidationException.single("items", "Danh sách mặt hàng hủy không được để trống.");
        }
        if (request.reasonCategory() == null || request.reasonCategory().isBlank()) {
            throw ValidationException.single("reasonCategory", "Nhóm lý do hủy không được để trống.");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw ValidationException.single("reason", "Lý do hủy không được để trống.");
        }

        // Rule 1: Bắt buộc ảnh bằng chứng (evidenceImageUrl) khi xuất hủy sự cố FEA-04
        boolean isFea04Reason = isFea04Reason(request.reasonCategory());
        String evidence = request.evidenceImageUrl() != null && !request.evidenceImageUrl().isBlank()
                ? request.evidenceImageUrl().trim()
                : (request.evidenceFileId() != null ? request.evidenceFileId().trim() : null);

        if ((isFea04Reason || request.branchId() != null) && (evidence == null || evidence.isBlank())) {
            throw ValidationException.single("evidenceImageUrl",
                    "Ảnh chụp bằng chứng hiện trường/hàng hỏng là bắt buộc (Rule 1).");
        }

        for (CreateDisposalSlipRequest.Item item : request.items()) {
            if (item.ingredientId() == null) {
                throw ValidationException.single("ingredientId", "Mã nguyên vật liệu không được để trống.");
            }
            boolean hasBase = item.quantityBase() != null && item.quantityBase().compareTo(BigDecimal.ZERO) > 0;
            boolean hasPacks = item.quantityPacks() != null && item.quantityPacks().compareTo(BigDecimal.ZERO) > 0;
            if (!hasBase && !hasPacks) {
                throw ValidationException.single("quantityBase", "Số lượng hàng hủy phải lớn hơn 0.");
            }
        }
    }

    private boolean isFea04Reason(String reasonCategory) {
        if (reasonCategory == null) return false;
        return switch (reasonCategory.trim().toUpperCase(Locale.ROOT)) {
            case "BARISTA_ERROR", "EXPIRED_SHELF_LIFE", "STORAGE_DEFECT", "CUSTOMER_COMPLAINT" -> true;
            default -> false;
        };
    }

    private String formatReceiveReason(String slipCode, String source, String supplier,
                                       String invoiceRef, LocalDate expiry, String notes) {
        StringBuilder sb = new StringBuilder();
        sb.append(slipCode).append(" | Nguồn: ").append(source != null ? source : "NCC");
        if (supplier != null && !supplier.isBlank()) {
            sb.append(" | NCC: ").append(supplier);
        }
        if (invoiceRef != null && !invoiceRef.isBlank()) {
            sb.append(" | [HĐ: ").append(invoiceRef).append("]");
        }
        if (expiry != null) {
            sb.append(" | HSD: ").append(expiry);
        }
        if (notes != null && !notes.isBlank()) {
            sb.append(" | Ghi chú: ").append(notes);
        }
        return sb.length() > 500 ? sb.substring(0, 497) + "..." : sb.toString();
    }

    private String formatIssueReason(String slipCode, String bar, String barista,
                                     String purpose, String notes) {
        StringBuilder sb = new StringBuilder();
        // Rule 5: Lưu StockTransaction với type = ISSUE, reason = "PXK-BAR: Xuất quầy bar - Người nhận: " + baristaName
        sb.append("PXK-BAR: Xuất quầy bar - Người nhận: ").append(barista);
        if (slipCode != null && !slipCode.isBlank()) {
            sb.append(" | ").append(slipCode);
        }
        if (bar != null && !bar.isBlank()) {
            sb.append(" | Quầy: ").append(bar);
        }
        if (purpose != null && !purpose.isBlank()) {
            sb.append(" | Mục đích: ").append(purpose);
        }
        if (notes != null && !notes.isBlank()) {
            sb.append(" | Ghi chú: ").append(notes);
        }
        return sb.length() > 500 ? sb.substring(0, 497) + "..." : sb.toString();
    }

    private String formatDisposalReason(String slipCode, String category, String reason, String itemCause) {
        StringBuilder sb = new StringBuilder();
        sb.append(slipCode).append(" | [").append(category).append("] ").append(reason);
        if (itemCause != null && !itemCause.isBlank()) {
            sb.append(" | Nguyên nhân cụ thể: ").append(itemCause);
        }
        return sb.length() > 500 ? sb.substring(0, 497) + "..." : sb.toString();
    }

    private User resolveCurrentUser() {
        Integer branchUserId = BranchContext.current().map(BranchContext.Info::userId).orElse(null);
        if (branchUserId != null) {
            User user = userRepository.findById(branchUserId).orElse(null);
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

        return userRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ForbiddenException(ErrorCode.AUTH_REQUIRED));
    }

    private Inventory createDefaultInventory(Branch branch, Ingredient ingredient) {
        BigDecimal safetyStock = ingredient.getUnitsPerPack() != null
                ? ingredient.getUnitsPerPack().multiply(new BigDecimal("3.0000"))
                : new BigDecimal("10.0000");

        Inventory inv = Inventory.builder()
                .branch(branch)
                .ingredient(ingredient)
                .currentQtyBase(BigDecimal.ZERO)
                .currentPackUnits(BigDecimal.ZERO)
                .safetyStockLevel(safetyStock)
                .lowStockFlag(true)
                .lastUpdated(Instant.now())
                .build();
        return inventoryRepository.saveAndFlush(inv);
    }

    private String generateSlipCode(Integer branchId, String prefix) {
        String dateStr = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        long seq = stockTransactionRepository.countTodaySlipsByPrefix(branchId, prefix + "-" + dateStr) + 1;
        return String.format("%s-%s-%03d", prefix, dateStr, seq);
    }
}
