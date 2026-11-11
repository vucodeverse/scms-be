package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.CreateIngredientRequest;
import edu.fpt.sba.scms.dto.request.UpdateIngredientRequest;
import edu.fpt.sba.scms.dto.response.IngredientDto;

import java.util.List;

public interface IngredientService {

    List<IngredientDto> list(String status);

    IngredientDto getById(Long id);

    IngredientDto create(CreateIngredientRequest request);

    IngredientDto update(Long id, UpdateIngredientRequest request);

    IngredientDto toggleStatus(Long id);

    IngredientDto toggleStatus(Long id, String status);
}
