package br.com.oficina.application.service;

import br.com.oficina.application.dto.PartRequestDto;
import br.com.oficina.application.dto.PartResponseDto;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.Part;
import br.com.oficina.infrastructure.repository.PartRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class PartService {

    public static final String PECA_INSUMO = "Peça/Insumo";
    PartRepository partRepository;

    public PartService(PartRepository partRepository) {
        this.partRepository = partRepository;
    }

    public List<PartResponseDto> listAll() {
        return partRepository.listAll().stream()
            .map(PartResponseDto::from)
            .toList();
    }

    public PartResponseDto findById(Long id) {
        Part part = partRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException(PECA_INSUMO, id));
        return PartResponseDto.from(part);
    }

    public List<PartResponseDto> findLowStock(int threshold) {
        return partRepository.findLowStock(threshold).stream()
            .map(PartResponseDto::from)
            .toList();
    }

    @Transactional
    public PartResponseDto create(PartRequestDto dto) {
        Part part = new Part(dto.name(), dto.description(), dto.unitPrice(), dto.stockQuantity(), dto.unit());
        partRepository.persist(part);
        return PartResponseDto.from(part);
    }

    @Transactional
    public PartResponseDto update(Long id, PartRequestDto dto) {
        Part part = partRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException(PECA_INSUMO, id));
        part.update(dto.name(), dto.description(), dto.unitPrice(), dto.stockQuantity(), dto.unit());
        return PartResponseDto.from(part);
    }

    @Transactional
    public PartResponseDto adjustStock(Long id, int adjustment) {
        Part part = partRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException(PECA_INSUMO, id));
        if (adjustment > 0) {
            part.increaseStock(adjustment);
        } else {
            part.decreaseStock(Math.abs(adjustment));
        }
        return PartResponseDto.from(part);
    }

    @Transactional
    public void delete(Long id) {
        Part part = partRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException(PECA_INSUMO, id));
        partRepository.delete(part);
    }
}
