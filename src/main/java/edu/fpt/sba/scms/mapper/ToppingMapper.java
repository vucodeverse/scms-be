package edu.fpt.sba.scms.mapper;

import edu.fpt.sba.scms.dto.response.ToppingDto;
import edu.fpt.sba.scms.model.Topping;
import org.mapstruct.Mapper;

import java.util.List;

/** Entity ↔ DTO của topping (UC11). */
@Mapper(componentModel = "spring")
public interface ToppingMapper {

    ToppingDto toDto(Topping topping);

    List<ToppingDto> toDtos(List<Topping> toppings);
}
