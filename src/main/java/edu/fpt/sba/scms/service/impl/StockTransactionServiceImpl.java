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
import edu.fpt.sba.scms.dto.request.CreateDisposalSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateIssueSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateReceiveSlipRequest;
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
import java.util.List;
import java.util.Map;

/**
 * Triển khai nghiệp vụ UC28 — Nhập kho, UC29 — Xuất kho cho quầy Bar, UC30 — Hủy nguyên vật liệu hỏng / hết hạn.
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
        validateReceiveRequest(request);

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockIssueSlipResponse issueStockToBar(CreateIssueSlipRequest request) {
        // 1. Kiểm tra ngữ cảnh chi nhánh (BR-01)
        Integer branchId = BranchContext.requireBranchId();
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("chi nhánh"));

        // Lấy thông tin người thực hiện giao dịch (BM)
        User currentUser = resolveCurrentUser();

        // 2. Validate dữ liệu đầu vào (Fail Fast)
        validateIssueRequest(request);

        // 3. Sinh mã phiếu xuất kho tự động PXK-YYYYMMDD-XXX
        String slipCode = generateSlipCode(branchId, "PXK");
        Instant now = Instant.now();

        List<StockIssueSlipResponse.ItemResponse> itemResponses = new ArrayList<>();

        // 4. Xử lý từng mặt hàng xuất kho
        for (CreateIssueSlipRequest.Item itemReq : request.items()) {
            Ingredient ingredient = ingredientRepository.findById(itemReq.ingredientId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.INGREDIENT_NOT_FOUND,
                            "Không tìm thấy nguyên vật liệu có ID: " + itemReq.ingredientId()));

            // Khóa Pessimistic Write để ngăn chặn 2 thread cùng đọc và xuất âm kho (BR-42)
            Inventory inventory = inventoryRepository.findForUpdate(branchId, ingredient.getId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.INVENTORY_NOT_FOUND,
                            "Nguyên liệu " + ingredient.getName() + " chưa có bản ghi tồn kho tại chi nhánh."));

            BigDecimal unitsPerPack = ingredient.getUnitsPerPack() != null
                    && ingredient.getUnitsPerPack().compareTo(BigDecimal.ZERO) > 0
                    ? ingredient.getUnitsPerPack() : BigDecimal.ONE;

            // Quy đổi kiện hàng sang đơn vị cơ sở (BR-41 & BR-42)
            BigDecimal baseQtyToDeduct = itemReq.quantityPacks().multiply(unitsPerPack);

            // BR-42: Chặn tuyệt đối xuất âm kho
            BigDecimal currentQty = inventory.getCurrentQtyBase() != null
                    ? inventory.getCurrentQtyBase() : BigDecimal.ZERO;
            if (currentQty.compareTo(baseQtyToDeduct) < 0) {
                throw new BusinessRuleException(ErrorCode.INSUFFICIENT_STOCK,
                        String.format("Tồn kho nguyên liệu '%s' hiện tại chỉ còn %s %s, không đủ để xuất %s %s (%s %s).",
                                ingredient.getName(), currentQty, ingredient.getBaseUnit(),
                                baseQtyToDeduct, ingredient.getBaseUnit(), itemReq.quantityPacks(), ingredient.getPackUnit()));
            }

            // Trừ tồn kho và tính lại số kiện & cờ tồn thấp (BR-45)
            inventory.setCurrentQtyBase(currentQty.subtract(baseQtyToDeduct));
            inventory.recalculateStockAndFlag(unitsPerPack);
            inventoryRepository.save(inventory);

            // Lưu bản ghi biến động kho (16 cột)
            String itemReason = formatIssueReason(slipCode, request.destinationBar(),
                    request.baristaName(), request.notes());

            StockTransaction tx = StockTransaction.builder()
                    .branch(branch)
                    .ingredient(ingredient)
                    .type(StockTransactionType.ISSUE)
                    .shiftId(null)
                    .stocktakeId(null)
                    .createdBy(currentUser)
                    .createdAt(now)
                    .cartons(null)
                    .looseUnits(null)
                    .purchasePrice(null)
                    .lineTotal(null)
                    .quantityPacks(itemReq.quantityPacks())
                    .quantityBase(baseQtyToDeduct)
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
                    itemReq.quantityPacks(),
                    baseQtyToDeduct,
                    inventory.getCurrentQtyBase(),
                    inventory.getCurrentPackUnits(),
                    inventory.getLowStockFlag()
            ));
        }

        // 5. Ghi nhật ký kiểm toán bất biến (BR-51)
        auditLogService.log(
                AuditAction.STOCK_ISSUE,
                ENTITY_STOCK_TRANSACTION,
                slipCode,
                null,
                Map.of(
                        "slipCode", slipCode,
                        "destinationBar", request.destinationBar(),
                        "baristaName", request.baristaName(),
                        "itemCount", request.items().size(),
                        "summary", String.format("Tạo phiếu xuất kho %s cho quầy %s (Người nhận: %s)",
                                slipCode, request.destinationBar(), request.baristaName())
                )
        );

        String issuedByName = currentUser.getFullName() != null && !currentUser.getFullName().isBlank()
                ? currentUser.getFullName() : currentUser.getUsername();

        return new StockIssueSlipResponse(
                slipCode,
                branchId,
                branch.getName(),
                request.purpose(),
                request.destinationBar(),
                request.baristaName(),
                issuedByName,
                now,
                request.notes(),
                itemResponses
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockDisposalSlipResponse disposeStock(CreateDisposalSlipRequest request) {
        // 1. Kiểm tra ngữ cảnh chi nhánh (BR-01)
        Integer branchId = BranchContext.requireBranchId();
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("chi nhánh"));

        // Lấy thông tin người thực hiện giao dịch (BM)
        User currentUser = resolveCurrentUser();

        // 2. Validate dữ liệu đầu vào & quy định BR-43 (Fail Fast)
        validateDisposalRequest(request);

        // 3. Sinh mã phiếu hủy kho tự động PHK-YYYYMMDD-XXX
        String slipCode = generateSlipCode(branchId, "PHK");
        Instant now = Instant.now();

        BigDecimal totalLoss = BigDecimal.ZERO;
        List<StockDisposalSlipResponse.ItemResponse> itemResponses = new ArrayList<>();

        // 4. Xử lý từng mặt hàng xin hủy
        for (CreateDisposalSlipRequest.Item itemReq : request.items()) {
            Ingredient ingredient = ingredientRepository.findById(itemReq.ingredientId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.INGREDIENT_NOT_FOUND,
                            "Không tìm thấy nguyên vật liệu có ID: " + itemReq.ingredientId()));

            // Khóa Pessimistic Write để đảm bảo toàn vẹn dữ liệu
            Inventory inventory = inventoryRepository.findForUpdate(branchId, ingredient.getId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.INVENTORY_NOT_FOUND,
                            "Nguyên liệu " + ingredient.getName() + " chưa có bản ghi tồn kho tại chi nhánh."));

            BigDecimal unitsPerPack = ingredient.getUnitsPerPack() != null
                    && ingredient.getUnitsPerPack().compareTo(BigDecimal.ZERO) > 0
                    ? ingredient.getUnitsPerPack() : BigDecimal.ONE;

            // Quy đổi sang đơn vị cơ sở để trừ kho
            BigDecimal baseQtyToDeduct = itemReq.quantityPacks().multiply(unitsPerPack);

            // BR-42: Không hủy vượt quá số lượng tồn khả dụng
            BigDecimal currentQty = inventory.getCurrentQtyBase() != null
                    ? inventory.getCurrentQtyBase() : BigDecimal.ZERO;
            if (currentQty.compareTo(baseQtyToDeduct) < 0) {
                throw new BusinessRuleException(ErrorCode.INSUFFICIENT_STOCK,
                        String.format("Tồn kho nguyên liệu '%s' không đủ để hủy (Tồn: %s %s, Yêu cầu hủy: %s %s).",
                                ingredient.getName(), currentQty, ingredient.getBaseUnit(),
                                baseQtyToDeduct, ingredient.getBaseUnit()));
            }

            // Trừ tồn kho và tính lại số kiện & cờ tồn thấp (BR-45)
            inventory.setCurrentQtyBase(currentQty.subtract(baseQtyToDeduct));
            inventory.recalculateStockAndFlag(unitsPerPack);
            inventoryRepository.save(inventory);

            // Tính toán tổn thất tài chính (Financial Loss): item_loss = quantity_packs * costPerPack
            BigDecimal unitCostBase = ingredient.getUnitCost() != null ? ingredient.getUnitCost() : BigDecimal.ZERO;
            BigDecimal costPerPack = unitCostBase.multiply(unitsPerPack).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineLoss = itemReq.quantityPacks().multiply(costPerPack).setScale(2, RoundingMode.HALF_UP);
            totalLoss = totalLoss.add(lineLoss);

            // Lưu bản ghi giao dịch DISPOSAL (16 cột, lưu ảnh bằng chứng vào evidence_image_url)
            String itemReason = formatDisposalReason(slipCode, request.reasonCategory(), request.reason(),
                    itemReq.itemCause(), request.evidenceFileId());

            StockTransaction tx = StockTransaction.builder()
                    .branch(branch)
                    .ingredient(ingredient)
                    .type(StockTransactionType.DISPOSAL)
                    .shiftId(null)
                    .stocktakeId(null)
                    .createdBy(currentUser)
                    .createdAt(now)
                    .cartons(null)
                    .looseUnits(null)
                    .purchasePrice(costPerPack)
                    .lineTotal(lineLoss)
                    .quantityPacks(itemReq.quantityPacks())
                    .quantityBase(baseQtyToDeduct)
                    .reason(itemReason)
                    .evidenceImageUrl(request.evidenceFileId())
                    .build();
            stockTransactionRepository.save(tx);

            itemResponses.add(new StockDisposalSlipResponse.ItemResponse(
                    tx.getId(),
                    ingredient.getId(),
                    ingredient.getName(),
                    ingredient.getPackUnit(),
                    ingredient.getBaseUnit(),
                    itemReq.quantityPacks(),
                    baseQtyToDeduct,
                    costPerPack,
                    lineLoss,
                    inventory.getCurrentQtyBase(),
                    inventory.getCurrentPackUnits(),
                    inventory.getLowStockFlag()
            ));
        }

        // 5. Ghi nhật ký kiểm toán bất biến (BR-51)
        auditLogService.log(
                AuditAction.STOCK_DISPOSAL,
                ENTITY_STOCK_TRANSACTION,
                slipCode,
                null,
                Map.of(
                        "slipCode", slipCode,
                        "reasonCategory", request.reasonCategory(),
                        "reason", request.reason(),
                        "totalFinancialLoss", totalLoss,
                        "itemCount", request.items().size(),
                        "summary", String.format("Tạo phiếu hủy kho %s. Nguyên nhân: [%s] %s. Tổng tổn thất: %s VND",
                                slipCode, request.reasonCategory(), request.reason(), MoneyUtil.format(totalLoss))
                )
        );

        String authorizedByName = currentUser.getFullName() != null && !currentUser.getFullName().isBlank()
                ? currentUser.getFullName() : currentUser.getUsername();

        return new StockDisposalSlipResponse(
                slipCode,
                branchId,
                branch.getName(),
                request.reasonCategory(),
                request.reason(),
                request.evidenceFileId(),
                authorizedByName,
                now,
                totalLoss,
                itemResponses
        );
    }

    private void validateReceiveRequest(CreateReceiveSlipRequest request) {
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

    private void validateIssueRequest(CreateIssueSlipRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw ValidationException.single("items", "Danh sách mặt hàng xuất kho không được để trống.");
        }
        if (request.destinationBar() == null || request.destinationBar().isBlank()) {
            throw ValidationException.single("destinationBar", "Quầy nhận hàng không được để trống.");
        }
        if (request.baristaName() == null || request.baristaName().isBlank()) {
            throw ValidationException.single("baristaName", "Tên người nhận không được để trống.");
        }

        for (CreateIssueSlipRequest.Item item : request.items()) {
            if (item.ingredientId() == null) {
                throw ValidationException.single("ingredientId", "Mã nguyên vật liệu không được để trống.");
            }
            if (item.quantityPacks() == null || item.quantityPacks().compareTo(BigDecimal.ZERO) <= 0) {
                throw ValidationException.single("quantityPacks", "Số lượng kiện xuất phải lớn hơn 0.");
            }
        }
    }

    private void validateDisposalRequest(CreateDisposalSlipRequest request) {
        if (request == null) {
            throw ValidationException.single("request", "Dữ liệu yêu cầu hủy kho không được để trống.");
        }
        // BR-43: Bắt buộc lý do chi tiết >= 10 ký tự
        if (request.reason() == null || request.reason().trim().length() < 10) {
            throw ValidationException.single("reason",
                    "Lý do hủy hàng bắt buộc phải mô tả chi tiết nguyên nhân (tối thiểu 10 ký tự) theo quy định BR-43.");
        }
        if (request.reasonCategory() == null || request.reasonCategory().isBlank()) {
            throw ValidationException.single("reasonCategory", "Phân loại nguyên nhân không được để trống.");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw ValidationException.single("items", "Danh sách mặt hàng hủy không được để trống.");
        }

        for (CreateDisposalSlipRequest.Item item : request.items()) {
            if (item.ingredientId() == null) {
                throw ValidationException.single("ingredientId", "Mã nguyên vật liệu không được để trống.");
            }
            if (item.quantityPacks() == null || item.quantityPacks().compareTo(BigDecimal.ZERO) <= 0) {
                throw ValidationException.single("quantityPacks", "Số lượng kiện hủy phải lớn hơn 0.");
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

    private String formatReceiveReason(String slipCode, String source, String supplierName,
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

    private String formatIssueReason(String slipCode, String destinationBar, String baristaName, String notes) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(slipCode).append("] ");
        sb.append("Xuất quầy bar: ").append(destinationBar != null ? destinationBar.trim() : "");
        sb.append(". Người nhận: ").append(baristaName != null ? baristaName.trim() : "");
        if (notes != null && !notes.isBlank()) {
            sb.append(". Ghi chú: ").append(notes.trim());
        }
        String result = sb.toString();
        if (result.length() > 500) {
            return result.substring(0, 500);
        }
        return result;
    }

    private String formatDisposalReason(String slipCode, String reasonCategory, String reason,
                                        String itemCause, String evidenceFileId) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(slipCode).append("] ");
        sb.append("[").append(reasonCategory != null ? reasonCategory.trim() : "").append("] ");
        sb.append(reason != null ? reason.trim() : "");
        if (itemCause != null && !itemCause.isBlank()) {
            sb.append(". Chi tiết: ").append(itemCause.trim());
        }
        if (evidenceFileId != null && !evidenceFileId.isBlank()) {
            sb.append(". Ảnh: ").append(evidenceFileId.trim());
        }
        String result = sb.toString();
        if (result.length() > 500) {
            return result.substring(0, 500);
        }
        return result;
    }
}
