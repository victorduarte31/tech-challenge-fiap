package br.com.oficina.infrastructure.persistence;

import br.com.oficina.domain.model.ServiceItem;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Traduz entre o modelo de domínio {@code ServiceItem} (puro) e a entidade de
 * persistência {@code ServiceItemEntity}.
 */
@ApplicationScoped
public class ServiceItemMapper {

    public ServiceItem toDomain(ServiceItemEntity e) {
        return ServiceItem.rehydrate(
            e.getId(), e.getName(), e.getDescription(), e.getBasePrice(),
            e.getEstimatedDurationMinutes(), e.getActive(), e.getCreatedAt(), e.getUpdatedAt());
    }

    public ServiceItemEntity toNewEntity(ServiceItem d) {
        ServiceItemEntity e = new ServiceItemEntity();
        applyState(e, d);
        return e;
    }

    /** Sincroniza o estado do domínio na entidade gerenciada. */
    public void applyState(ServiceItemEntity e, ServiceItem d) {
        e.setName(d.getName());
        e.setDescription(d.getDescription());
        e.setBasePrice(d.getBasePrice());
        e.setEstimatedDurationMinutes(d.getEstimatedDurationMinutes());
        e.setActive(d.getActive());
    }
}
