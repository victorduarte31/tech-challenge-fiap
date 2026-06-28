package br.com.oficina.infrastructure.persistence;

import br.com.oficina.domain.model.Vehicle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Traduz entre o modelo de domínio {@code Vehicle} (puro) e a entidade de
 * persistência {@code VehicleEntity}. A FK do cliente é resolvida por proxy
 * ({@code em.getReference}) — apenas o id é gravado, sem carregar a entidade.
 */
@ApplicationScoped
public class VehicleMapper {

    @Inject
    EntityManager em;

    public Vehicle toDomain(VehicleEntity e) {
        return Vehicle.rehydrate(
            e.getId(), e.getLicensePlate(), e.getBrand(), e.getModel(), e.getProductionYear(),
            e.getClient().getId(), e.getClient().getName(), e.getCreatedAt(), e.getUpdatedAt());
    }

    public VehicleEntity toNewEntity(Vehicle d) {
        VehicleEntity e = new VehicleEntity();
        applyState(e, d);
        return e;
    }

    /** Sincroniza o estado do domínio na entidade gerenciada. */
    public void applyState(VehicleEntity e, Vehicle d) {
        e.setLicensePlate(d.getLicensePlate());
        e.setBrand(d.getBrand());
        e.setModel(d.getModel());
        e.setProductionYear(d.getProductionYear());
        e.setClient(em.getReference(ClientEntity.class, d.getClientId()));
    }
}
