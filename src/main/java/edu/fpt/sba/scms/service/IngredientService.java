package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.response.IngredientDto;

import java.util.List;

public interface IngredientService {

    List<IngredientDto> list(String status);
}
