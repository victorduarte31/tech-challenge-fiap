package br.com.oficina.application.service;

import br.com.oficina.application.Pagination;
import br.com.oficina.application.dto.VehicleRequestDto;
import br.com.oficina.application.dto.VehicleResponseDto;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.Vehicle;
import br.com.oficina.application.ports.out.ClientRepositoryPort;
import br.com.oficina.application.ports.out.VehicleRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class VehicleService {

    public static final String VEICULO = "Veículo";
    VehicleRepositoryPort vehicleRepository;
    ClientRepositoryPort clientRepository;

    public VehicleService(VehicleRepositoryPort vehicleRepository, ClientRepositoryPort clientRepository) {
        this.vehicleRepository = vehicleRepository;
        this.clientRepository = clientRepository;
    }

    public List<VehicleResponseDto> listAll(int page, int size) {
        return vehicleRepository.listAll(Pagination.page(page), Pagination.cap(size)).stream()
            .map(VehicleResponseDto::from)
            .toList();
    }

    /** Total de veículos, para o cabeçalho X-Total-Count. */
    public long countAll() {
        return vehicleRepository.countAll();
    }

    public VehicleResponseDto findById(Long id) {
        Vehicle vehicle = vehicleRepository.fetchById(id)
            .orElseThrow(() -> new ResourceNotFoundException(VEICULO, id));
        return VehicleResponseDto.from(vehicle);
    }

    public List<VehicleResponseDto> findByClientId(Long clientId) {
        return vehicleRepository.findByClientId(clientId).stream()
            .map(VehicleResponseDto::from)
            .toList();
    }

    @Transactional
    public VehicleResponseDto create(VehicleRequestDto dto) {
        String normalized = normalizeLicensePlate(dto.licensePlate());
        if (vehicleRepository.existsByLicensePlate(normalized)) {
            throw new BusinessException("Placa já cadastrada: " + dto.licensePlate());
        }
        ensureClientExists(dto.clientId());

        Vehicle vehicle = new Vehicle(normalized, dto.brand(), dto.model(), dto.productionYear(), dto.clientId());
        return VehicleResponseDto.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleResponseDto update(Long id, VehicleRequestDto dto) {
        Vehicle vehicle = vehicleRepository.fetchById(id)
            .orElseThrow(() -> new ResourceNotFoundException(VEICULO, id));

        String normalized = normalizeLicensePlate(dto.licensePlate());
        if (!vehicle.getLicensePlate().equals(normalized) && vehicleRepository.existsByLicensePlate(normalized)) {
            throw new BusinessException("Placa já cadastrada: " + dto.licensePlate());
        }
        ensureClientExists(dto.clientId());

        vehicle.update(normalized, dto.brand(), dto.model(), dto.productionYear(), dto.clientId());
        return VehicleResponseDto.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public void delete(Long id) {
        vehicleRepository.fetchById(id)
            .orElseThrow(() -> new ResourceNotFoundException(VEICULO, id));
        vehicleRepository.removeById(id);
    }

    private void ensureClientExists(Long clientId) {
        clientRepository.fetchById(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Cliente", clientId));
    }

    private String normalizeLicensePlate(String plate) {
        return plate.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }
}
