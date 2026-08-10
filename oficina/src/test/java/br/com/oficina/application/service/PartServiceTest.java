package br.com.oficina.application.service;

import br.com.oficina.application.dto.PartRequestDto;
import br.com.oficina.application.dto.PartResponseDto;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.Part;
import br.com.oficina.domain.model.PartType;
import br.com.oficina.application.ports.out.PartRepositoryPort;
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
class PartServiceTest {

    @Mock
    PartRepositoryPort partRepository;

    @InjectMocks
    PartService partService;

    private Part samplePart;

    @BeforeEach
    void setUp() {
        samplePart = new Part("Óleo Motor 5W30", "1 litro", new BigDecimal("45.90"), 20, "L");
        DomainTestFixtures.setId(samplePart, 1L);
        DomainTestFixtures.setField(samplePart, "createdAt", LocalDateTime.now());
        DomainTestFixtures.setField(samplePart, "updatedAt", LocalDateTime.now());
    }

    @Test
    void listAll_shouldReturnAllParts() {
        when(partRepository.listActive(0, 20)).thenReturn(List.of(samplePart));

        List<PartResponseDto> result = partService.listAll(0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Óleo Motor 5W30");
    }

    @Test
    void findById_whenExists_shouldReturn() {
        when(partRepository.fetchById(1L)).thenReturn(Optional.of(samplePart));

        PartResponseDto result = partService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.unitPrice()).isEqualByComparingTo("45.90");
    }

    @Test
    void findById_whenNotFound_shouldThrow() {
        when(partRepository.fetchById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partService.findById(99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findLowStock_shouldReturnPartsAtOrBelowMinimum() {
        when(partRepository.findLowStock()).thenReturn(List.of(samplePart));

        List<PartResponseDto> result = partService.findLowStock();

        assertThat(result).hasSize(1);
    }

    @Test
    void create_shouldPersistAndReturn() {
        when(partRepository.save(any(Part.class))).thenAnswer(inv -> inv.getArgument(0));

        PartRequestDto dto = new PartRequestDto(
            "Filtro de Ar", "Filtro", new BigDecimal("29.90"), 10, "UN", 5, PartType.PECA
        );

        PartResponseDto result = partService.create(dto);

        assertThat(result.name()).isEqualTo("Filtro de Ar");
        assertThat(result.stockQuantity()).isEqualTo(10);
        verify(partRepository).save(any(Part.class));
    }

    @Test
    void update_whenExists_shouldUpdateFields() {
        when(partRepository.fetchById(1L)).thenReturn(Optional.of(samplePart));
        when(partRepository.save(any(Part.class))).thenAnswer(inv -> inv.getArgument(0));

        PartRequestDto dto = new PartRequestDto(
            "Óleo Atualizado", "2 litros", new BigDecimal("89.90"), 15, "L", 3, PartType.INSUMO
        );

        PartResponseDto result = partService.update(1L, dto);

        assertThat(result.name()).isEqualTo("Óleo Atualizado");
        assertThat(result.stockQuantity()).isEqualTo(15);
    }

    @Test
    void adjustStock_increase_shouldAddToStock() {
        when(partRepository.fetchById(1L)).thenReturn(Optional.of(samplePart));
        when(partRepository.save(any(Part.class))).thenAnswer(inv -> inv.getArgument(0));

        PartResponseDto result = partService.adjustStock(1L, 10);

        assertThat(result.stockQuantity()).isEqualTo(30);
    }

    @Test
    void adjustStock_decrease_shouldSubtractFromStock() {
        when(partRepository.fetchById(1L)).thenReturn(Optional.of(samplePart));
        when(partRepository.save(any(Part.class))).thenAnswer(inv -> inv.getArgument(0));

        PartResponseDto result = partService.adjustStock(1L, -5);

        assertThat(result.stockQuantity()).isEqualTo(15);
    }

    @Test
    void adjustStock_belowZero_shouldThrowException() {
        when(partRepository.fetchById(1L)).thenReturn(Optional.of(samplePart));

        assertThatThrownBy(() -> partService.adjustStock(1L, -30))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Estoque insuficiente");
    }

    @Test
    void adjustStock_withZero_shouldThrowException() {
        assertThatThrownBy(() -> partService.adjustStock(1L, 0))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("não pode ser zero");
    }

    @Test
    void delete_whenExists_shouldSoftDelete() {
        when(partRepository.fetchById(1L)).thenReturn(Optional.of(samplePart));

        assertThatCode(() -> partService.delete(1L)).doesNotThrowAnyException();

        // Soft-delete: peça é desativada (não removida fisicamente) e persistida
        assertThat(samplePart.isActive()).isFalse();
        verify(partRepository).save(samplePart);
    }

    @Test
    void delete_whenNotFound_shouldThrow() {
        when(partRepository.fetchById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partService.delete(99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reactivate_whenExists_shouldActivatePart() {
        samplePart.deactivate();
        when(partRepository.fetchById(1L)).thenReturn(Optional.of(samplePart));
        when(partRepository.save(any(Part.class))).thenAnswer(inv -> inv.getArgument(0));

        PartResponseDto result = partService.reactivate(1L);

        assertThat(samplePart.isActive()).isTrue();
        assertThat(result.active()).isTrue();
    }

    @Test
    void reactivate_whenNotFound_shouldThrow() {
        when(partRepository.fetchById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partService.reactivate(99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
