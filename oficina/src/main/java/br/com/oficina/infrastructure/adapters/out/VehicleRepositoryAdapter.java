package br.com.oficina.infrastructure.adapters.out;

import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.Vehicle;
import br.com.oficina.domain.ports.out.VehicleRepositoryPort;
import br.com.oficina.infrastructure.persistence.VehicleEntity;
import br.com.oficina.infrastructure.persistence.VehicleMapper;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

/**
 * Adapter de persistência do aggregate {@code Vehicle}. Opera sobre
 * {@code VehicleEntity} via Panache e expõe à aplicação apenas o domínio puro,
 * traduzido pelo {@link VehicleMapper}.
 */
@ApplicationScoped
public class VehicleRepositoryAdapter implements VehicleRepositoryPort, PanacheRepository<VehicleEntity> {

    @Inject
    VehicleMapper mapper;

    @Override
    public List<Vehicle> listAll(int page, int size) {
        return findAll(Sort.by("id")).page(Page.of(page, size)).list().stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public Optional<Vehicle> fetchById(Long id) {
        return findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public List<Vehicle> findByClientId(Long clientId) {
        return list("client.id", clientId).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public boolean existsByLicensePlate(String licensePlate) {
        return count("licensePlate", licensePlate) > 0;
    }

    @Override
    public Vehicle save(Vehicle vehicle) {
        VehicleEntity entity;
        if (vehicle.getId() == null) {
            entity = mapper.toNewEntity(vehicle);
            persist(entity);
        } else {
            entity = findById(vehicle.getId());
            if (entity == null) {
                throw new ResourceNotFoundException("Veículo", vehicle.getId());
            }
            mapper.applyState(entity, vehicle);
        }
        flush();
        return mapper.toDomain(entity);
    }

    @Override
    public void removeById(Long id) {
        deleteById(id);
    }
}
