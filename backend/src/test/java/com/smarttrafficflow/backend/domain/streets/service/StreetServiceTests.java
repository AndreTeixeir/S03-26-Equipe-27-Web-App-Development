package com.smarttrafficflow.backend.domain.streets.service;

import com.smarttrafficflow.backend.api.dto.StreetResponse;
import com.smarttrafficflow.backend.api.dto.StreetSearchResponse;
import com.smarttrafficflow.backend.domain.streets.entity.Street;
import com.smarttrafficflow.backend.domain.streets.repository.StreetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StreetService tests")
class StreetServiceTests {

    @Mock
    private StreetRepository streetRepository;

    @InjectMocks
    private StreetService streetService;

    @Test
    @DisplayName("returns a street by its osm way id")
    void returnsStreetByOsmWayId() {
        Street street = street(101L, "Avenida Central");
        when(streetRepository.findByOsmWayId(101L)).thenReturn(Optional.of(street));

        Street result = streetService.getByOsmWayId(101L);

        assertThat(result).isEqualTo(street);
    }

    @Test
    @DisplayName("rejects unknown osm way ids")
    void rejectsUnknownOsmWayIds() {
        when(streetRepository.findByOsmWayId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> streetService.getByOsmWayId(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("streetOsmWayId invalido: 999");
    }

    @Test
    @DisplayName("normalizes search defaults and trims the query")
    void normalizesSearchDefaults() {
        when(streetRepository.searchByName("avenida", 20, 0)).thenReturn(List.of(
                street(101L, "Avenida Central")
        ));
        when(streetRepository.countByNameFilter("avenida")).thenReturn(1L);

        StreetSearchResponse response = streetService.search("  avenida  ", null, null);

        assertThat(response.limit()).isEqualTo(20);
        assertThat(response.offset()).isEqualTo(0);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).extracting(StreetResponse::name).containsExactly("Avenida Central");
    }

    @Test
    @DisplayName("caps search limit and normalizes negative paging values")
    void capsSearchLimitAndNormalizesNegativePaging() {
        when(streetRepository.searchByName("", 50, 0)).thenReturn(List.of());
        when(streetRepository.countByNameFilter("")).thenReturn(0L);

        StreetSearchResponse response = streetService.search("   ", 999, -7);

        assertThat(response.limit()).isEqualTo(50);
        assertThat(response.offset()).isEqualTo(0);
        verify(streetRepository).searchByName("", 50, 0);
    }

    @Test
    @DisplayName("returns the sampled random street osm way ids")
    void returnsRandomStreetOsmWayIds() {
        when(streetRepository.findRandomOsmWayIds(3)).thenReturn(List.of(303L, 101L));

        assertThat(streetService.getRandomStreetOsmWayIds(3)).containsExactly(303L, 101L);
    }

    @Test
    @DisplayName("rejects random street requests when no streets were imported")
    void rejectsRandomStreetRequestsWhenNoStreetExists() {
        when(streetRepository.findRandomOsmWayIds(5)).thenReturn(List.of());

        assertThatThrownBy(() -> streetService.getRandomStreetOsmWayIds(5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nenhuma rua real importada");
    }

    private Street street(long osmWayId, String name) {
        Street street = new Street();
        street.setId(UUID.randomUUID());
        street.setOsmWayId(osmWayId);
        street.setName(name);
        return street;
    }
}
