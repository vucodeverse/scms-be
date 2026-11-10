package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.exception.ConflictException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.dto.request.SaveToppingRequest;
import edu.fpt.sba.scms.dto.response.ToppingDto;
import edu.fpt.sba.scms.mapper.ToppingMapper;
import edu.fpt.sba.scms.model.Topping;
import edu.fpt.sba.scms.repository.OrderItemToppingRepository;
import edu.fpt.sba.scms.repository.ProductToppingRepository;
import edu.fpt.sba.scms.repository.ToppingRepository;
import edu.fpt.sba.scms.service.ToppingService;
import edu.fpt.sba.scms.service.support.MasterDataText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** UC11 — danh mục topping. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToppingServiceImpl implements ToppingService {

    private static final String ENTITY = "topping";
    private static final String RESOURCE_NAME = "topping";

    private final ToppingRepository toppingRepository;
    private final ProductToppingRepository productToppingRepository;
    private final OrderItemToppingRepository orderItemToppingRepository;
    private final ToppingMapper toppingMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public List<ToppingDto> list() {
        return toppingMapper.toDtos(toppingRepository.findAllByOrderByNameAsc());
    }

    @Override
    @Transactional
    public ToppingDto create(SaveToppingRequest request) {
        String name = MasterDataText.requireText(request.name());
        if (toppingRepository.existsByName(name)) {
            throw new ConflictException(ErrorCode.DUPLICATE_NAME);
        }
        Topping topping = toppingRepository.save(Topping.builder()
                .name(name)
                .defaultPrice(request.defaultPrice())
                .status(request.status())
                .build());

        auditLogService.log(AuditAction.PRICE_UPDATE, ENTITY, topping.getId(), null, Map.of("name", name));
        log.info("Tạo topping: id={}, name={}", topping.getId(), name);
        return toppingMapper.toDto(topping);
    }

    @Override
    @Transactional
    public ToppingDto update(Integer id, SaveToppingRequest request) {
        Topping topping = getTopping(id);
        String name = MasterDataText.requireText(request.name());
        if (toppingRepository.existsByNameAndIdNot(name, id)) {
            throw new ConflictException(ErrorCode.DUPLICATE_NAME);
        }
        String previousName = topping.getName();
        topping.setName(name);
        topping.setDefaultPrice(request.defaultPrice());
        topping.setStatus(request.status());

        auditLogService.log(AuditAction.PRICE_UPDATE, ENTITY, id, Map.of("name", previousName),
                Map.of("name", name, "defaultPrice", request.defaultPrice()));
        return toppingMapper.toDto(topping);
    }

    @Override
    @Transactional
    public ToppingDto softDelete(Integer id) {
        Topping topping = getTopping(id);
        topping.setStatus(RecordStatus.INACTIVE);
        auditLogService.log(AuditAction.PRICE_UPDATE, ENTITY, id, null, Map.of("status", RecordStatus.INACTIVE.name()));
        return toppingMapper.toDto(topping);
    }

    @Override
    @Transactional
    public void hardDelete(Integer id) {
        Topping topping = getTopping(id);
        if (orderItemToppingRepository.existsByToppingId(id)) {
            throw new ConflictException(ErrorCode.RESOURCE_CONFLICT,
                    "Topping đã phát sinh trong đơn hàng. Chỉ có thể xoá mềm (ngừng bán).");
        }
        long linkedProducts = productToppingRepository.countByToppingId(id);
        // Gỡ liên kết trước để không còn dòng product_topping trỏ tới topping đã xoá (bảng không có FK cascade).
        productToppingRepository.deleteAllByToppingId(id);
        productToppingRepository.flush();
        toppingRepository.delete(topping);

        auditLogService.log(AuditAction.PRICE_UPDATE, ENTITY, id, Map.of("name", topping.getName()),
                Map.of("unlinkedProducts", linkedProducts));
        log.info("Xoá cứng topping: id={}, gỡ khỏi {} sản phẩm", id, linkedProducts);
    }

    private Topping getTopping(Integer id) {
        return toppingRepository.findById(id).orElseThrow(() -> new NotFoundException(RESOURCE_NAME));
    }
}
