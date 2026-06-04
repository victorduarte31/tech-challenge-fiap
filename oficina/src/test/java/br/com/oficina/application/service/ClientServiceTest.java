package br.com.oficina.application.service;

import br.com.oficina.application.dto.ClientRequestDto;
import br.com.oficina.application.dto.ClientResponseDto;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.Client;
import br.com.oficina.domain.model.ClientType;
import br.com.oficina.infrastructure.repository.ClientRepository;
import br.com.oficina.testsupport.DomainTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    ClientRepository clientRepository;

    @InjectMocks
    ClientService clientService;

    private Client sampleClient;

    @BeforeEach
    void setUp() {
        sampleClient = new Client("João Silva", "11144477735", ClientType.PF, "joao@example.com", "11999999999");
        DomainTestFixtures.setId(sampleClient, 1L);
        DomainTestFixtures.setField(sampleClient, "createdAt", LocalDateTime.now());
        DomainTestFixtures.setField(sampleClient, "updatedAt", LocalDateTime.now());
    }

    @Test
    void listAll_shouldReturnMappedDtos() {
        when(clientRepository.listAll(0, 20)).thenReturn(List.of(sampleClient));

        List<ClientResponseDto> result = clientService.listAll(0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("João Silva");
        assertThat(result.getFirst().cpfCnpj()).isEqualTo("11144477735");
    }

    @Test
    void findById_whenExists_shouldReturnDto() {
        when(clientRepository.findByIdOptional(1L)).thenReturn(Optional.of(sampleClient));

        ClientResponseDto result = clientService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("João Silva");
    }

    @Test
    void findById_whenNotFound_shouldThrowNotFoundException() {
        when(clientRepository.findByIdOptional(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.findById(99L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("99");
    }

    @Test
    void findByCpfCnpj_whenExists_shouldReturnDto() {
        when(clientRepository.findByCpfCnpj("11144477735")).thenReturn(Optional.of(sampleClient));

        ClientResponseDto result = clientService.findByCpfCnpj("111.444.777-35");

        assertThat(result.cpfCnpj()).isEqualTo("11144477735");
    }

    @Test
    void findByCpfCnpj_whenNotFound_shouldThrowNotFoundException() {
        when(clientRepository.findByCpfCnpj(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.findByCpfCnpj("11144477735"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_withNewCpfCnpj_shouldPersistAndReturn() {
        when(clientRepository.existsByCpfCnpj("11144477735")).thenReturn(false);
        doNothing().when(clientRepository).persist(any(Client.class));

        ClientRequestDto dto = new ClientRequestDto(
            "João Silva", "111.444.777-35", ClientType.PF, "joao@email.com", "11999"
        );

        ClientResponseDto result = clientService.create(dto);

        assertThat(result.name()).isEqualTo("João Silva");
        assertThat(result.cpfCnpj()).isEqualTo("11144477735");
        verify(clientRepository).persist(any(Client.class));
    }

    @Test
    void create_withDuplicateCpfCnpj_shouldThrowBusinessException() {
        when(clientRepository.existsByCpfCnpj("11144477735")).thenReturn(true);

        ClientRequestDto dto = new ClientRequestDto(
            "João Silva", "111.444.777-35", ClientType.PF, null, null
        );

        assertThatThrownBy(() -> clientService.create(dto))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("CPF/CNPJ");
    }

    @Test
    void update_whenExists_shouldUpdateFields() {
        when(clientRepository.findByIdOptional(1L)).thenReturn(Optional.of(sampleClient));
        when(clientRepository.existsByCpfCnpj("52998224725")).thenReturn(false);

        ClientRequestDto dto = new ClientRequestDto(
            "João Updated", "529.982.247-25", ClientType.PF, "new@email.com", "11888"
        );

        ClientResponseDto result = clientService.update(1L, dto);

        assertThat(result.name()).isEqualTo("João Updated");
        assertThat(result.cpfCnpj()).isEqualTo("52998224725");
    }

    @Test
    void update_whenNotFound_shouldThrow() {
        when(clientRepository.findByIdOptional(99L)).thenReturn(Optional.empty());

        ClientRequestDto dto = new ClientRequestDto(
            "Test", "11144477735", ClientType.PF, null, null
        );

        assertThatThrownBy(() -> clientService.update(99L, dto))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_whenExists_shouldDeleteClient() {
        when(clientRepository.findByIdOptional(1L)).thenReturn(Optional.of(sampleClient));
        doNothing().when(clientRepository).delete(sampleClient);

        assertThatCode(() -> clientService.delete(1L)).doesNotThrowAnyException();
        verify(clientRepository).delete(sampleClient);
    }

    @Test
    void delete_whenNotFound_shouldThrow() {
        when(clientRepository.findByIdOptional(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.delete(99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
