package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.dto.response.IngredientDto;
import edu.fpt.sba.scms.model.Ingredient;
import edu.fpt.sba.scms.repository.IngredientRepository;
import edu.fpt.sba.scms.service.IngredientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientServiceImpl implements IngredientService {

    private final IngredientRepository ingredientRepository;

    @Override
    public List<IngredientDto> list(String status) {
        List<Ingredient> ingredients;
        if ("ALL".equalsIgnoreCase(status)) {
            ingredients = ingredientRepository.findAll();
        } else {
            String resolvedStatus = status != null ? status.toUpperCase() : "ACTIVE";
            ingredients = ingredientRepository.findAllByStatusOrderByNameAsc(resolvedStatus);
        }
        return ingredients.stream()
                .map(this::toDto)
                .toList();
    }

    private IngredientDto toDto(Ingredient entity) {
        return new IngredientDto(
                entity.getId(),
                entity.getName(),
                entity.getBaseUnit(),
                entity.getPackUnit(),
                entity.getUnitsPerPack(),
                entity.getUnitCost(),
                entity.getStatus()
        );
    }
}
