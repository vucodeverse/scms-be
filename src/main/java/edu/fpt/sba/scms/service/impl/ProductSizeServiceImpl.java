package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.dto.request.SaveProductSizeRequest;
import edu.fpt.sba.scms.dto.response.ProductSizeDto;
import edu.fpt.sba.scms.mapper.ProductMapper;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.ProductSize;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.repository.ProductSizeRepository;
import edu.fpt.sba.scms.repository.RecipeRepository;
import edu.fpt.sba.scms.service.ProductSizeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** UC11 — bảng giá theo size. Ghi dữ liệu luôn khoá sản phẩm trước để serialise upsert cùng một size. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSizeServiceImpl implements ProductSizeService {

    private static final String ENTITY = "product_size";

    private final ProductRepository productRepository;
    private final ProductSizeRepository productSizeRepository;
    private final RecipeRepository recipeRepository;
    private final ProductMapper productMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public List<ProductSizeDto> list(Integer productId) {
        requireProduct(productId);
        return productSizeRepository.findAllByProductIdOrderBySizeAsc(productId).stream()
                .map(productMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public ProductSizeDto upsert(Integer productId, SaveProductSizeRequest request) {
        // Khoá dòng sản phẩm: hai request upsert cùng size chạy tuần tự, tránh vi phạm UNIQUE(product_id, size).
        requireProductForUpdate(productId);
        ProductSize size = productSizeRepository.findByProductIdAndSize(productId, request.size())
                .orElseGet(() -> ProductSize.builder()
                        .productId(productId)
                        .size(request.size())
                        .build());
        size.setPrice(request.price());
        productSizeRepository.saveAndFlush(size);

        auditLogService.log(AuditAction.PRICE_UPDATE, ENTITY, size.getId(),
                Map.of("productId", productId, "size", request.size().name()), Map.of("price", request.price()));
        log.info("Cập nhật giá size: productId={}, size={}, price={}", productId, request.size(), request.price());
        return productMapper.toDto(size);
    }

    @Override
    @Transactional
    public void delete(Integer productId, ProductSizeName size) {
        Product product = requireProductForUpdate(productId);
        ProductSize productSize = productSizeRepository.findByProductIdAndSize(productId, size)
                .orElseThrow(() -> new NotFoundException("giá size " + size));
        if (size == product.getDefaultSize()) {
            throw new BusinessRuleException(BusinessErrorCode.DEFAULT_SIZE_IN_USE,
                    "Không xoá size mặc định của sản phẩm. Đổi size mặc định trước.");
        }
        recipeRepository.deleteByProductIdAndSize(productId, size);
        productSizeRepository.delete(productSize);
        auditLogService.log(AuditAction.PRICE_UPDATE, ENTITY, productSize.getId(), Map.of("size", size.name()), null);
    }

    private Product requireProduct(Integer productId) {
        return productRepository.findById(productId).orElseThrow(() -> new NotFoundException("sản phẩm"));
    }

    private Product requireProductForUpdate(Integer productId) {
        return productRepository.findByIdForUpdate(productId).orElseThrow(() -> new NotFoundException("sản phẩm"));
    }
}
