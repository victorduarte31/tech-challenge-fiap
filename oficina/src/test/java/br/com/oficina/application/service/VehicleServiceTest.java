package br.com.oficina.application.service;

import br.com.oficina.application.dto.VehicleRequestDto;
import br.com.oficina.application.dto.VehicleResponseDto;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.Client;
import br.com.oficina.domain.model.ClientType;
import br.com.oficina.domain.model.Vehicle;
import br.com.oficina.infrastructure.repository.ClientRepository;
import br.com.oficina.infrastructure.repository.VehicleRepository;
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
class VehicleServiceTest {

    @Mock VehicleRepository vehicleRepository;
    @Mock ClientRepository clientRepository;

    @InjectMocks
    VehicleService vehicleService;

    private Client client;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        client = new Client("João", "11144477735", ClientType.PF, null, null);
        DomainTestFixtures.setId(client, 1L);

        vehicle = new Vehicle("ABC1234", "Toyota", "Corolla", 2020, client);
        DomainTestFixtures.setId(vehicle, 1L);
        DomainTestFixtures.setField(vehicle, "createdAt", LocalDateTime.now());
        DomainTestFixtures.setField(vehicle, "updatedAt", LocalDateTime.now());
    }

    @Test
    void listAll_shouldReturnAll() {
        when(vehicleRepository.listAll()).thenReturn(List.of(vehicle));

        List<VehicleResponseDto> result = vehicleService.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().licensePlate()).isEqualTo("ABC1234");
    }

    @Test
    void findById_whenExists_shouldReturn() {
        when(vehicleRepository.findByIdOptional(1L)).thenReturn(Optional.of(vehicle));

        VehicleResponseDto result = vehicleService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void findById_whenNotFound_shouldThrow() {
        when(vehicleRepository.findByIdOptional(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.findById(99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByClientId_shouldReturnClientVehicles() {
        when(vehicleRepository.findByClientId(1L)).thenReturn(List.of(vehicle));

        List<VehicleResponseDto> result = vehicleService.findByClientId(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void create_withNewPlate_shouldPersistAndReturn() {
        when(vehicleRepository.existsByLicensePlate("ABC1234")).thenReturn(false);
        when(clientRepository.findByIdOptional(1L)).thenReturn(Optional.of(client));
        doNothing().when(vehicleRepository).persist(any(Vehicle.class));

        VehicleRequestDto dto = new VehicleRequestDto("ABC-1234", "Toyota", "Corolla", 2020, 1L);

        VehicleResponseDto result = vehicleService.create(dto);

        assertThat(result.brand()).isEqualTo("Toyota");
        verify(vehicleRepository).persist(any(Vehicle.class));
    }

    @Test
    void create_withDuplicatePlate_shouldThrowBusinessException() {
        when(vehicleRepository.existsByLicensePlate("ABC1234")).thenReturn(true);

        VehicleRequestDto dto = new VehicleRequestDto("ABC-1234", "Toyota", "Corolla", 2020, 1L);

        assertThatThrownBy(() -> vehicleService.create(dto))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Placa");
    }

    @Test
    void create_withUnknownClient_shouldThrowNotFoundException() {
        when(vehicleRepository.existsByLicensePlate("ABC1234")).thenReturn(false);
        when(clientRepository.findByIdOptional(99L)).thenReturn(Optional.empty());

        VehicleRequestDto dto = new VehicleRequestDto("ABC-1234", "Toyota", "Corolla", 2020, 99L);

        assertThatThrownBy(() -> vehicleService.create(dto))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_whenExists_shouldDelete() {
        when(vehicleRepository.findByIdOptional(1L)).thenReturn(Optional.of(vehicle));
        doNothing().when(vehicleRepository).delete(vehicle);

        assertThatCode(() -> vehicleService.delete(1L)).doesNotThrowAnyException();
        verify(vehicleRepository).delete(vehicle);
    }
}
