package br.com.oficina.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositório Panache de {@code VehicleEntity}, usado por composição pelo adapter de
 * persistência — ver {@link WorkOrderPanacheRepository} para a justificativa de
 * não herdar {@code PanacheRepository} direto no adapter.
 */
@ApplicationScoped
public class VehiclePanacheRepository implements PanacheRepository<VehicleEntity> {
}
