package br.com.oficina.application.service;

import br.com.oficina.application.Pagination;
import br.com.oficina.application.dto.PartRequestDto;
import br.com.oficina.application.dto.PartResponseDto;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.Part;
import br.com.oficina.domain.ports.out.PartRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class PartService {

    public static final String PECA_INSUMO = "Peça/Insumo";
    PartRepositoryPort partRepository;

    public PartService(PartRepositoryPort partRepository) {
        this.partRepository = partRepository;
    }

    public List<PartResponseDto> listAll(int page, int size) {
        return partRepository.listActive(Pagination.page(page), Pagination.cap(size)).stream()
            .map(PartResponseDto::from)
            .toList();
    }

    public PartResponseDto findById(Long id) {
        Part part = partRepository.fetchById(id)
            .orElseThrow(() -> new ResourceNotFoundException(PECA_INSUMO, id));
        return PartResponseDto.from(part);
    }

    public List<PartResponseDto> findLowStock() {
        return partRepository.findLowStock().stream()
            .map(PartResponseDto::from)
            .toList();
    }

    @Transactional
    public PartResponseDto create(PartRequestDto dto) {
        Part part = new Part(dto.name(), dto.description(), dto.unitPrice(), dto.stockQuantity(), dto.unit(),
            dto.minimumStock(), dto.partType());
        return PartResponseDto.from(partRepository.save(part));
    }

    @Transactional
    public PartResponseDto update(Long id, PartRequestDto dto) {
        Part part = partRepository.fetchById(id)
            .orElseThrow(() -> new ResourceNotFoundException(PECA_INSUMO, id));
        part.update(dto.name(), dto.description(), dto.unitPrice(), dto.stockQuantity(), dto.unit(),
            dto.minimumStock(), dto.partType());
        return PartResponseDto.from(partRepository.save(part));
    }

    @Transactional
    public PartResponseDto adjustStock(Long id, int adjustment) {
        if (adjustment == 0) {
            throw new BusinessException("O ajuste de estoque não pode ser zero");
        }
        Part part = partRepository.fetchById(id)
            .orElseThrow(() -> new ResourceNotFoundException(PECA_INSUMO, id));
        if (adjustment > 0) {
            part.increaseStock(adjustment);
        } else {
            part.decreaseStock(Math.abs(adjustment));
        }
        return PartResponseDto.from(partRepository.save(part));
    }

    /**
     * Exclusão lógica (soft-delete): a peça pode estar referenciada por OS históricas,
     * portanto não é removida fisicamente — apenas desativada, saindo do catálogo e dos alertas.
     */
    @Transactional
    public void delete(Long id) {
        Part part = partRepository.fetchById(id)
            .orElseThrow(() -> new ResourceNotFoundException(PECA_INSUMO, id));
        part.deactivate();
        partRepository.save(part);
    }

    /**
     * Reverte o soft-delete: reativa uma peça previamente desativada, devolvendo-a
     * ao catálogo e aos alertas de reposição.
     */
    @Transactional
    public PartResponseDto reactivate(Long id) {
        Part part = partRepository.fetchById(id)
            .orElseThrow(() -> new ResourceNotFoundException(PECA_INSUMO, id));
        part.activate();
        return PartResponseDto.from(partRepository.save(part));
    }
}
