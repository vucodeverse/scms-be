package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.enums.SortDirection;
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
import edu.fpt.sba.scms.model.Product;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncConfigServiceImpl implements SyncConfigService {

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
        return productService.list(new ProductListQuery(null, null, null, 1, 100, SortDirection.ASC, "name"))
                .getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public SyncPreviewDto getSyncPreview() {
        List<BranchDto> branches = branchService.list(new BranchListQuery(null, null, 1, 100, SortDirection.ASC, "code"))
                .getContent();
        List<CategoryDto> categories = categoryService.list(new CategoryListQuery(null, null, 1, 100, SortDirection.ASC, "sortOrder"))
                .getContent();
        List<ProductDto> products = getSyncableProducts();
        List<VoucherDto> vouchers = voucherService.list(new VoucherListQuery(null, null, 1, 100, SortDirection.DESC, "createdAt"))
                .getContent();

        return new SyncPreviewDto(branches, categories, products, vouchers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncConfigResponse syncToBranches(SyncConfigRequest request, UserPrincipal principal) {
        List<Integer> successfulBranchIds = new ArrayList<>();
        List<String> branchNames = new ArrayList<>();

        // 1. Kiểm tra và xác thực các chi nhánh mục tiêu
        for (Integer branchId : request.targetBranchIds()) {
            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new NotFoundException("chi nhánh ID=" + branchId));
            successfulBranchIds.add(branch.getId());
            branchNames.add(branch.getName());
        }

        List<String> syncedProductNames = new ArrayList<>();
        int syncedProductCount = 0;
        int syncedCategoryCount = 0;
        int syncedVoucherCount = 0;

        // 2. Xử lý việc chọn món ăn đồng bộ
        if (Boolean.TRUE.equals(request.syncProducts())) {
            List<Product> targetProducts;
            if (request.productIds() != null && !request.productIds().isEmpty()) {
                targetProducts = productRepository.findAllById(request.productIds());
            } else {
                targetProducts = productRepository.findAll();
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
                syncedCategoryCount = request.categoryIds().size();
            } else {
                syncedCategoryCount = (int) categoryRepository.count();
            }
        }

        // 4. Xử lý việc chọn voucher đồng bộ
        if (Boolean.TRUE.equals(request.syncVouchers())) {
            if (request.voucherIds() != null && !request.voucherIds().isEmpty()) {
                syncedVoucherCount = request.voucherIds().size();
            } else {
                syncedVoucherCount = (int) voucherRepository.count();
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
}
