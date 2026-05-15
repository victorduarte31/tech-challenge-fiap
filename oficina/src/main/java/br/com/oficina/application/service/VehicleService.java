package br.com.oficina.application.service;

import br.com.oficina.application.dto.VehicleRequestDto;
import br.com.oficina.application.dto.VehicleResponseDto;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.Client;
import br.com.oficina.domain.model.Vehicle;
import br.com.oficina.infrastructure.repository.ClientRepository;
import br.com.oficina.infrastructure.repository.VehicleRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class VehicleService {

    public static final String VEICULO = "Veículo";
    VehicleRepository vehicleRepository;

    ClientRepository clientRepository;

    public VehicleService(VehicleRepository vehicleRepository, ClientRepository clientRepository) {
        this.vehicleRepository = vehicleRepository;
        this.clientRepository = clientRepository;
    }

    public List<VehicleResponseDto> listAll() {
        return vehicleRepository.listAll().stream()
            .map(VehicleResponseDto::from)
            .toList();
    }

    public VehicleResponseDto findById(Long id) {
        Vehicle vehicle = vehicleRepository.findByIdOptional(id)
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
        Client client = clientRepository.findByIdOptional(dto.clientId())
            .orElseThrow(() -> new ResourceNotFoundException("Cliente", dto.clientId()));

        Vehicle vehicle = new Vehicle(normalized, dto.brand(), dto.model(), dto.productionYear(), client);
        vehicleRepository.persist(vehicle);
        return VehicleResponseDto.from(vehicle);
    }

    @Transactional
    public VehicleResponseDto update(Long id, VehicleRequestDto dto) {
        Vehicle vehicle = vehicleRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException(VEICULO, id));

        String normalized = normalizeLicensePlate(dto.licensePlate());
        if (!vehicle.getLicensePlate().equals(normalized) && vehicleRepository.existsByLicensePlate(normalized)) {
            throw new BusinessException("Placa já cadastrada: " + dto.licensePlate());
        }
        Client client = clientRepository.findByIdOptional(dto.clientId())
            .orElseThrow(() -> new ResourceNotFoundException("Cliente", dto.clientId()));

        vehicle.update(normalized, dto.brand(), dto.model(), dto.productionYear(), client);
        return VehicleResponseDto.from(vehicle);
    }

    @Transactional
    public void delete(Long id) {
        Vehicle vehicle = vehicleRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException(VEICULO, id));
        vehicleRepository.delete(vehicle);
    }

    private String normalizeLicensePlate(String plate) {
        return plate.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }
}
