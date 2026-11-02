package edu.fpt.sba.scms.service.support;

import edu.fpt.sba.scms.common.enums.SortDirection;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MasterDataQueriesTest {

    private static final Map<String, Sort> SORTS = Map.of(
            "name", Sort.by("name"),
            "createdAt", Sort.by("createdAt"));

    @Test
    void sort_uses_default_when_client_does_not_send_sortBy() {
        Sort result = MasterDataQueries.sort(null, SortDirection.DESC, SORTS, Sort.by("sortOrder"));
        assertThat(result).containsExactly(Sort.Order.desc("sortOrder"));
    }

    @Test
    void sort_direction_is_applied_to_whitelisted_field() {
        Sort result = MasterDataQueries.sort("name", SortDirection.DESC, SORTS, Sort.by("sortOrder"));
        assertThat(result).containsExactly(Sort.Order.desc("name"));
    }

    @Test
    void sort_rejects_field_outside_whitelist() {
        assertThatThrownBy(() -> MasterDataQueries.sort("passwordHash", SortDirection.ASC, SORTS,
                Sort.by("sortOrder")))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("passwordHash");
    }

    @Test
    void pageable_defaults_to_page_1_size_10_when_params_missing() {
        Pageable pageable = MasterDataQueries.pageable(null, null, Sort.by("id"));
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
    }

    @Test
    void pageable_converts_1_based_page_to_zero_based() {
        Pageable pageable = MasterDataQueries.pageable(3, 25, Sort.by("id"));
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(25);
    }

    @Test
    void trimToNull_collapses_blank_input_to_null() {
        assertThat(MasterDataText.trimToNull("   ")).isNull();
        assertThat(MasterDataText.trimToNull(null)).isNull();
        assertThat(MasterDataText.trimToNull("  Trà sữa ")).isEqualTo("Trà sữa");
    }
}
