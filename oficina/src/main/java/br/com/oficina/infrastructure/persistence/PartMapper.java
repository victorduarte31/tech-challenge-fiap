package br.com.oficina.infrastructure.persistence;

import br.com.oficina.domain.model.Part;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Traduz entre o modelo de domínio {@code Part} (puro) e a entidade de
 * persistência {@code PartEntity}.
 */
@ApplicationScoped
public class PartMapper {

    public Part toDomain(PartEntity e) {
        return Part.rehydrate(
            e.getId(), e.getName(), e.getDescription(), e.getUnitPrice(), e.getStockQuantity(),
            e.getUnit(), e.getMinimumStock(), e.getPartType(), e.getActive(), e.getVersion(),
            e.getCreatedAt(), e.getUpdatedAt());
    }

    public PartEntity toNewEntity(Part d) {
        PartEntity e = new PartEntity();
        applyState(e, d);
        return e;
    }

    /** Sincroniza o estado do domínio na entidade gerenciada. */
    public void applyState(PartEntity e, Part d) {
        e.setName(d.getName());
        e.setDescription(d.getDescription());
        e.setUnitPrice(d.getUnitPrice());
        e.setStockQuantity(d.getStockQuantity());
        e.setUnit(d.getUnit());
        e.setMinimumStock(d.getMinimumStock());
        e.setPartType(d.getPartType());
        e.setActive(d.getActive());
    }
}
