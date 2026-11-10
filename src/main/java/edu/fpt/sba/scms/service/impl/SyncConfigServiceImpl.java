package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.enums.SortDirection;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.dto.request.SyncConfigRequest;
import edu.fpt.sba.scms.dto.request.query.BranchListQuery;
import edu.fpt.sba.scms.dto.request.query.CategoryListQuery;
import edu.fpt.sba.scms.dto.request.query.ProductListQuery;
import edu.fpt.sba.scms.dto.request.query.VoucherListQuery;
import edu.fpt.sba.scms.dto.response.BranchDto;
import edu.fpt.sba.scms.dto.response.CategoryDto;
import edu.fpt.sba.scms.dto.response.ProductDto;
import edu.fpt.sba.scms.dto.response.SyncConfigResponse;
import edu.fpt.sba.scms.dto.response.SyncPreviewDto;
import edu.fpt.sba.scms.dto.response.VoucherDto;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Category;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.Voucher;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.CategoryRepository;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.repository.VoucherRepository;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.BranchService;
import edu.fpt.sba.scms.service.CategoryService;
import edu.fpt.sba.scms.service.ProductService;
import edu.fpt.sba.scms.service.SyncConfigService;
import edu.fpt.sba.scms.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncConfigServiceImpl implements SyncConfigService {

    private static final int PAGE_BATCH = 100;

    private final BranchRepository branchRepository;
    private final BranchService branchService;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final VoucherRepository voucherRepository;
    private final VoucherService voucherService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> getSyncableProducts() {
        return fetchAllPages(page -> productService.list(
                new ProductListQuery(null, null, null, page, PAGE_BATCH, SortDirection.ASC, "name")));
    }

    @Override
    @Transactional(readOnly = true)
    public SyncPreviewDto getSyncPreview() {
        List<BranchDto> branches = fetchAllPages(page -> branchService.list(
                new BranchListQuery(null, null, page, PAGE_BATCH, SortDirection.ASC, "code")));
        List<CategoryDto> categories = fetchAllPages(page -> categoryService.list(
                new CategoryListQuery(null, null, page, PAGE_BATCH, SortDirection.ASC, "sortOrder")));
        List<ProductDto> products = getSyncableProducts();
        List<VoucherDto> vouchers = fetchAllPages(page -> voucherService.list(
                new VoucherListQuery(null, null, page, PAGE_BATCH, SortDirection.DESC, "createdAt")));

        return new SyncPreviewDto(branches, categories, products, vouchers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncConfigResponse syncToBranches(SyncConfigRequest request, UserPrincipal principal) {
        if (!Boolean.TRUE.equals(request.syncCategories())
                && !Boolean.TRUE.equals(request.syncProducts())
                && !Boolean.TRUE.equals(request.syncVouchers())) {
            throw new InvalidFieldValueException("syncCategories",
                    "Phải chọn ít nhất một nhóm dữ liệu (Danh mục, Sản phẩm hoặc Voucher) để đồng bộ.");
        }

        List<Integer> successfulBranchIds = new ArrayList<>();
        List<String> branchNames = new ArrayList<>();

        // 1. Kiểm tra và xác thực các chi nhánh mục tiêu
        List<Integer> distinctBranchIds = request.targetBranchIds().stream().distinct().toList();
        for (Integer branchId : distinctBranchIds) {
            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new NotFoundException("chi nhánh ID=" + branchId));
            if (branch.getStatus() == BranchStatus.INACTIVE) {
                throw new InvalidFieldValueException("targetBranchIds",
                        "Không thể đồng bộ xuống chi nhánh đã ngừng hoạt động (ID=" + branchId + ").");
            }
            successfulBranchIds.add(branch.getId());
            branchNames.add(branch.getName());
        }

        Map<Integer, Category> categoriesById = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        List<String> syncedProductNames = new ArrayList<>();
        int syncedProductCount = 0;
        int syncedCategoryCount = 0;
        int syncedVoucherCount = 0;

        // 2. Xử lý việc chọn món ăn đồng bộ
        if (Boolean.TRUE.equals(request.syncProducts())) {
            List<Product> targetProducts;
            if (request.productIds() != null && !request.productIds().isEmpty()) {
                List<Integer> distinctProductIds = request.productIds().stream().distinct().toList();
                targetProducts = productRepository.findAllById(distinctProductIds);
                if (targetProducts.size() != distinctProductIds.size()) {
                    throw new InvalidFieldValueException("productIds", "Có sản phẩm không tồn tại.");
                }
                for (Product p : targetProducts) {
                    if (p.getStatus() == ProductStatus.INACTIVE) {
                        throw new InvalidFieldValueException("productIds",
                                "Không thể đồng bộ sản phẩm đã ngừng kinh doanh: " + p.getName());
                    }
                    Category cat = categoriesById.get(p.getCategoryId());
                    if (cat == null || cat.isSystemCategory() || cat.getStatus() != RecordStatus.ACTIVE) {
                        throw new InvalidFieldValueException("productIds",
                                "Sản phẩm '" + p.getName() + "' không thuộc danh mục đang hoạt động.");
                    }
                }
            } else {
                targetProducts = productRepository.findAll().stream()
                        .filter(p -> p.getStatus() != ProductStatus.INACTIVE)
                        .filter(p -> {
                            Category cat = categoriesById.get(p.getCategoryId());
                            return cat != null && !cat.isSystemCategory() && cat.getStatus() == RecordStatus.ACTIVE;
                        })
                        .toList();
            }

            // Kích hoạt trạng thái AVAILABLE cho các món được chọn đồng bộ để chi nhánh có thể bán được ngay
            for (Product p : targetProducts) {
                if (p.getStatus() == null || p.getStatus() == ProductStatus.UNAVAILABLE) {
                    p.setStatus(ProductStatus.AVAILABLE);
                }
                syncedProductNames.add(p.getName());
            }
            productRepository.saveAll(targetProducts);
            syncedProductCount = targetProducts.size();
        }

        // 3. Xử lý việc chọn danh mục đồng bộ
        if (Boolean.TRUE.equals(request.syncCategories())) {
            if (request.categoryIds() != null && !request.categoryIds().isEmpty()) {
                List<Integer> distinctCategoryIds = request.categoryIds().stream().distinct().toList();
                List<Category> cats = categoryRepository.findAllById(distinctCategoryIds);
                if (cats.size() != distinctCategoryIds.size()) {
                    throw new InvalidFieldValueException("categoryIds", "Có danh mục không tồn tại.");
                }
                boolean hasInvalid = cats.stream()
                        .anyMatch(c -> c.isSystemCategory() || c.getStatus() != RecordStatus.ACTIVE);
                if (hasInvalid) {
                    throw new InvalidFieldValueException("categoryIds",
                            "Không thể đồng bộ danh mục hệ thống hoặc danh mục đã ngừng hoạt động.");
                }
                syncedCategoryCount = cats.size();
            } else {
                syncedCategoryCount = (int) categoriesById.values().stream()
                        .filter(c -> !c.isSystemCategory() && c.getStatus() == RecordStatus.ACTIVE)
                        .count();
            }
        }

        // 4. Xử lý việc chọn voucher đồng bộ
        if (Boolean.TRUE.equals(request.syncVouchers())) {
            Instant now = Instant.now();
            if (request.voucherIds() != null && !request.voucherIds().isEmpty()) {
                List<Integer> distinctVoucherIds = request.voucherIds().stream().distinct().toList();
                List<Voucher> vouchers = voucherRepository.findAllById(distinctVoucherIds);
                if (vouchers.size() != distinctVoucherIds.size()) {
                    throw new InvalidFieldValueException("voucherIds", "Có voucher không tồn tại.");
                }
                for (Voucher v : vouchers) {
                    if (v.getStatus() != RecordStatus.ACTIVE) {
                        throw new InvalidFieldValueException("voucherIds",
                                "Voucher '" + v.getCode() + "' đang ngừng hoạt động.");
                    }
                    if (v.getEndDate() != null && v.getEndDate().isBefore(now)) {
                        throw new InvalidFieldValueException("voucherIds",
                                "Voucher '" + v.getCode() + "' đã hết hạn.");
                    }
                }
                syncedVoucherCount = vouchers.size();
            } else {
                syncedVoucherCount = (int) voucherRepository.findAll().stream()
                        .filter(v -> v.getStatus() == RecordStatus.ACTIVE
                                && (v.getEndDate() == null || !v.getEndDate().isBefore(now)))
                        .count();
            }
        }

        String details = String.format("Đồng bộ chuỗi: %d món ăn %s, %d danh mục, %d voucher xuống %d chi nhánh %s",
                syncedProductCount,
                syncedProductNames,
                syncedCategoryCount,
                syncedVoucherCount,
                successfulBranchIds.size(),
                branchNames);

        auditLogService.log(AuditAction.SYNC_PUBLISH, "chain_sync", null, null, details);

        log.info("COO '{}' đồng bộ thành công {} món ăn tới {} chi nhánh: {}",
                principal != null ? principal.username() : "system",
                syncedProductCount,
                successfulBranchIds.size(),
                successfulBranchIds);

        String message = String.format("Đồng bộ thành công %d món ăn, %d danh mục, %d voucher tới %d chi nhánh (%s).",
                syncedProductCount,
                syncedCategoryCount,
                syncedVoucherCount,
                successfulBranchIds.size(),
                String.join(", ", branchNames));

        return new SyncConfigResponse(
                true,
                successfulBranchIds,
                Instant.now(),
                message,
                syncedProductCount,
                syncedCategoryCount,
                syncedVoucherCount,
                syncedProductNames
        );
    }

    private static <T> List<T> fetchAllPages(IntFunction<Page<T>> pageFetcher) {
        List<T> all = new ArrayList<>();
        int page = 1;
        while (true) {
            Page<T> current = pageFetcher.apply(page);
            all.addAll(current.getContent());
            if (!current.hasNext()) {
                break;
            }
            page++;
        }
        return all;
    }
}
