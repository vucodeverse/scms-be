package edu.fpt.sba.scms.integration.inventory;

import edu.fpt.sba.scms.model.Ingredient;
import edu.fpt.sba.scms.repository.IngredientRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test: IngredientLookupAdapter (Hexagonal Port Adapter)")
class IngredientLookupAdapterTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @InjectMocks
    private IngredientLookupAdapter adapter;

    @Test
    @DisplayName("Trả về Map rỗng khi danh sách id null hoặc rỗng")
    void shouldReturnEmptyMapWhenInputIsNullOrEmpty() {
        assertThat(adapter.findByIds(null)).isEmpty();
        assertThat(adapter.findByIds(Collections.emptyList())).isEmpty();

        verify(ingredientRepository, never()).findAllById(anyList());
    }

    @Test
    @DisplayName("Trả về Map rỗng khi danh sách id chỉ chứa null")
    void shouldReturnEmptyMapWhenInputContainsOnlyNulls() {
        assertThat(adapter.findByIds(Arrays.asList(null, null))).isEmpty();
        verify(ingredientRepository, never()).findAllById(anyList());
    }

    @Test
    @DisplayName("Tra cứu thành công và map chính xác thông tin nguyên liệu")
    void shouldLookupAndMapIngredientsSuccessfully() {
        Ingredient ing1 = Ingredient.builder()
                .id(1L)
                .name("Hạt Cà phê Robusta Đắk Lắk")
                .baseUnit("g")
                .packUnit("Bao 5kg")
                .unitsPerPack(new BigDecimal("5000"))
                .unitCost(new BigDecimal("150"))
                .status("ACTIVE")
                .build();

        Ingredient ing2 = Ingredient.builder()
                .id(2L)
                .name("Sữa đặc có đường Phương Nam")
                .baseUnit("can")
                .packUnit("Thùng 24 lon")
                .unitsPerPack(new BigDecimal("24"))
                .unitCost(new BigDecimal("22000"))
                .status("ACTIVE")
                .build();

        when(ingredientRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(ing1, ing2));

        Map<Integer, IngredientLookupPort.IngredientInfo> result = adapter.findByIds(List.of(1, 2));

        assertThat(result).hasSize(2);
        assertThat(result.get(1)).isNotNull();
        assertThat(result.get(1).id()).isEqualTo(1);
        assertThat(result.get(1).name()).isEqualTo("Hạt Cà phê Robusta Đắk Lắk");
        assertThat(result.get(1).baseUnit()).isEqualTo("g");

        assertThat(result.get(2)).isNotNull();
        assertThat(result.get(2).id()).isEqualTo(2);
        assertThat(result.get(2).name()).isEqualTo("Sữa đặc có đường Phương Nam");
        assertThat(result.get(2).baseUnit()).isEqualTo("can");
    }
}
