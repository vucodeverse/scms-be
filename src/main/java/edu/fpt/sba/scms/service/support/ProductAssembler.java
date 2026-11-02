package edu.fpt.sba.scms.service.support;

import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.dto.response.ProductDto;
import edu.fpt.sba.scms.dto.response.ProductSizeDto;
import edu.fpt.sba.scms.dto.response.ProductToppingDto;
import edu.fpt.sba.scms.mapper.ProductMapper;
import edu.fpt.sba.scms.model.Category;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.ProductSize;
import edu.fpt.sba.scms.model.ProductTopping;
import edu.fpt.sba.scms.model.Topping;
import edu.fpt.sba.scms.repository.ProductSizeRepository;
import edu.fpt.sba.scms.repository.ProductToppingRepository;
import edu.fpt.sba.scms.repository.ToppingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gom dữ liệu con của sản phẩm (size, topping, tên danh mục) theo <b>lô</b> cho cả một trang.
 * <p>
 * Mọi truy vấn dùng {@code IN (ids)} và kết quả nhóm theo khoá ngoài → trang 10 sản phẩm tốn số câu truy vấn không đổi.
 * Chỉ hiển thị topping ACTIVE: topping đã ngừng không còn bán.
 */
@Component
@RequiredArgsConstructor
public class ProductAssembler {

    private final ProductSizeRepository productSizeRepository;
    private final ProductToppingRepository productToppingRepository;
    private final ToppingRepository toppingRepository;
    private final ProductMapper productMapper;

    /**
     * @param products   dòng của một trang (giữ nguyên thứ tự)
     * @param categories danh mục của các sản phẩm — đoạn mã gọi đã truy vấn sẵn bằng {@code IN}
     */
    public List<ProductDto> toDtos(List<Product> products, Map<Integer, Category> categories) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Integer> productIds = products.stream().map(Product::getId).toList();
        Map<Integer, List<ProductSizeDto>> sizes = sizesByProduct(productIds);
        Map<Integer, List<ProductToppingDto>> toppings = toppingsByProduct(productIds);

        return products.stream()
                .map(product -> {
                    Category category = categories.get(product.getCategoryId());
                    return productMapper.toDto(product,
                            category == null ? null : category.getName(),
                            category == null ? null : category.getCode(),
                            sizes.getOrDefault(product.getId(), List.of()),
                            toppings.getOrDefault(product.getId(), List.of()));
                })
                .toList();
    }

    /** Size của một sản phẩm (sắp theo S, M, L). */
    public List<ProductSizeDto> sizes(Integer productId) {
        return productSizeRepository.findAllByProductIdOrderBySizeAsc(productId).stream()
                .map(productMapper::toDto)
                .toList();
    }

    /** Topping ACTIVE gắn với một sản phẩm (view detail). */
    public List<ProductToppingDto> toppings(Integer productId) {
        return toppingsByProduct(List.of(productId)).getOrDefault(productId, List.of());
    }

    private Map<Integer, List<ProductSizeDto>> sizesByProduct(Collection<Integer> productIds) {
        return productSizeRepository.findAllByProductIdIn(List.copyOf(productIds)).stream()
                .collect(Collectors.groupingBy(ProductSize::getProductId,
                        Collectors.mapping(productMapper::toDto, Collectors.toList())));
    }

    private Map<Integer, List<ProductToppingDto>> toppingsByProduct(Collection<Integer> productIds) {
        List<ProductTopping> links = productToppingRepository.findAllByProductIdIn(List.copyOf(productIds));
        if (links.isEmpty()) {
            return Map.of();
        }
        List<Integer> toppingIds = links.stream().map(ProductTopping::getToppingId).distinct().toList();
        Map<Integer, Topping> activeToppings = toppingRepository
                .findAllByIdInAndStatus(toppingIds, RecordStatus.ACTIVE).stream()
                .collect(Collectors.toMap(Topping::getId, Function.identity()));

        return links.stream()
                .filter(link -> activeToppings.containsKey(link.getToppingId()))
                .collect(Collectors.groupingBy(ProductTopping::getProductId,
                        Collectors.mapping(link -> new ProductToppingDto(link.getToppingId(),
                                activeToppings.get(link.getToppingId()).getName(),
                                link.getExtraPrice(), link.isDefault()), Collectors.toList())));
    }
}
