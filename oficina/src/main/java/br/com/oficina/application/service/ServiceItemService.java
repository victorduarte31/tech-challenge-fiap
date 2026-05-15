package br.com.oficina.application.service;

import br.com.oficina.application.dto.ServiceItemRequestDto;
import br.com.oficina.application.dto.ServiceItemResponseDto;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.ServiceItem;
import br.com.oficina.infrastructure.repository.ServiceItemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class ServiceItemService {

    public static final String SERVICO = "Serviço";
    ServiceItemRepository serviceItemRepository;

    public ServiceItemService(ServiceItemRepository serviceItemRepository) {
        this.serviceItemRepository = serviceItemRepository;
    }

    public List<ServiceItemResponseDto> listAll() {
        return serviceItemRepository.listAll().stream()
            .map(ServiceItemResponseDto::from)
            .toList();
    }

    public List<ServiceItemResponseDto> listActive() {
        return serviceItemRepository.findAllActive().stream()
            .map(ServiceItemResponseDto::from)
            .toList();
    }

    public ServiceItemResponseDto findById(Long id) {
        ServiceItem item = serviceItemRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException(SERVICO, id));
        return ServiceItemResponseDto.from(item);
    }

    @Transactional
    public ServiceItemResponseDto create(ServiceItemRequestDto dto) {
        ServiceItem item = new ServiceItem(dto.name(), dto.description(), dto.basePrice(), dto.estimatedDurationMinutes());
        serviceItemRepository.persist(item);
        return ServiceItemResponseDto.from(item);
    }

    @Transactional
    public ServiceItemResponseDto update(Long id, ServiceItemRequestDto dto) {
        ServiceItem item = serviceItemRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException(SERVICO, id));
        item.update(dto.name(), dto.description(), dto.basePrice(), dto.estimatedDurationMinutes());
        return ServiceItemResponseDto.from(item);
    }

    @Transactional
    public ServiceItemResponseDto deactivate(Long id) {
        ServiceItem item = serviceItemRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException(SERVICO, id));
        item.deactivate();
        return ServiceItemResponseDto.from(item);
    }

    @Transactional
    public void delete(Long id) {
        ServiceItem item = serviceItemRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException(SERVICO, id));
        serviceItemRepository.delete(item);
    }
}
