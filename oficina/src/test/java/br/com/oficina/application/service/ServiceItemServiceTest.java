package br.com.oficina.application.service;

import br.com.oficina.application.dto.ServiceItemRequestDto;
import br.com.oficina.application.dto.ServiceItemResponseDto;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.ServiceItem;
import br.com.oficina.infrastructure.repository.ServiceItemRepository;
import br.com.oficina.testsupport.DomainTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceItemServiceTest {

    @Mock
    ServiceItemRepository serviceItemRepository;

    @InjectMocks
    ServiceItemService serviceItemService;

    private ServiceItem sampleItem;

    @BeforeEach
    void setUp() {
        sampleItem = new ServiceItem("Troca de Óleo", "Troca completa de óleo e filtro",
            new BigDecimal("120.00"), 30);
        DomainTestFixtures.setId(sampleItem, 1L);
        DomainTestFixtures.setField(sampleItem, "createdAt", LocalDateTime.now());
        DomainTestFixtures.setField(sampleItem, "updatedAt", LocalDateTime.now());
    }

    @Test
    void listAll_shouldReturnAll() {
        when(serviceItemRepository.listAll()).thenReturn(List.of(sampleItem));

        List<ServiceItemResponseDto> result = serviceItemService.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Troca de Óleo");
    }

    @Test
    void listActive_shouldReturnOnlyActive() {
        when(serviceItemRepository.findAllActive()).thenReturn(List.of(sampleItem));

        List<ServiceItemResponseDto> result = serviceItemService.listActive();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().active()).isTrue();
    }

    @Test
    void findById_whenExists_shouldReturn() {
        when(serviceItemRepository.findByIdOptional(1L)).thenReturn(Optional.of(sampleItem));

        ServiceItemResponseDto result = serviceItemService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.basePrice()).isEqualByComparingTo("120.00");
    }

    @Test
    void findById_whenNotFound_shouldThrow() {
        when(serviceItemRepository.findByIdOptional(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceItemService.findById(99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_shouldPersistAndReturn() {
        doNothing().when(serviceItemRepository).persist(any(ServiceItem.class));

        ServiceItemRequestDto dto = new ServiceItemRequestDto(
            "Alinhamento", "Alinhamento das rodas", new BigDecimal("80.00"), 45
        );

        ServiceItemResponseDto result = serviceItemService.create(dto);

        assertThat(result.name()).isEqualTo("Alinhamento");
        assertThat(result.active()).isTrue();
        verify(serviceItemRepository).persist(any(ServiceItem.class));
    }

    @Test
    void update_whenExists_shouldUpdateFields() {
        when(serviceItemRepository.findByIdOptional(1L)).thenReturn(Optional.of(sampleItem));

        ServiceItemRequestDto dto = new ServiceItemRequestDto(
            "Troca de Óleo Premium", "Troca completa premium", new BigDecimal("150.00"), 40
        );

        ServiceItemResponseDto result = serviceItemService.update(1L, dto);

        assertThat(result.name()).isEqualTo("Troca de Óleo Premium");
        assertThat(result.basePrice()).isEqualByComparingTo("150.00");
    }

    @Test
    void deactivate_whenExists_shouldSetInactive() {
        when(serviceItemRepository.findByIdOptional(1L)).thenReturn(Optional.of(sampleItem));

        ServiceItemResponseDto result = serviceItemService.deactivate(1L);

        assertThat(result.active()).isFalse();
    }

    @Test
    void delete_whenExists_shouldDelete() {
        when(serviceItemRepository.findByIdOptional(1L)).thenReturn(Optional.of(sampleItem));
        doNothing().when(serviceItemRepository).delete(sampleItem);

        assertThatCode(() -> serviceItemService.delete(1L)).doesNotThrowAnyException();
        verify(serviceItemRepository).delete(sampleItem);
    }

    @Test
    void delete_whenNotFound_shouldThrow() {
        when(serviceItemRepository.findByIdOptional(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceItemService.delete(99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
