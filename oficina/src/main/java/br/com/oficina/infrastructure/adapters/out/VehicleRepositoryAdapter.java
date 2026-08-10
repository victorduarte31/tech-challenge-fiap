package br.com.oficina.infrastructure.adapters.out;

import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.Vehicle;
import br.com.oficina.application.ports.out.VehicleRepositoryPort;
import br.com.oficina.infrastructure.persistence.VehicleEntity;
import br.com.oficina.infrastructure.persistence.VehicleMapper;
import br.com.oficina.infrastructure.persistence.VehiclePanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

/**
 * Adapter de persistência do aggregate {@code Vehicle}. Opera sobre
 * {@code VehicleEntity} via Panache (por composição) e expõe à aplicação apenas o domínio puro,
 * traduzido pelo {@link VehicleMapper}.
 */
@ApplicationScoped
public class VehicleRepositoryAdapter implements VehicleRepositoryPort {

    @Inject
    VehicleMapper mapper;

    @Inject
    VehiclePanacheRepository repository;

    @Override
    public List<Vehicle> listAll(int page, int size) {
        return repository.findAll(Sort.by("id")).page(Page.of(page, size)).list().stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public Optional<Vehicle> fetchById(Long id) {
        return repository.findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public List<Vehicle> findByClientId(Long clientId) {
        return repository.list("client.id", clientId).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public boolean existsByLicensePlate(String licensePlate) {
        return repository.count("licensePlate", licensePlate) > 0;
    }

    @Override
    public Vehicle save(Vehicle vehicle) {
        VehicleEntity entity;
        if (vehicle.getId() == null) {
            entity = mapper.toNewEntity(vehicle);
            repository.persist(entity);
        } else {
            entity = repository.findById(vehicle.getId());
            if (entity == null) {
                throw new ResourceNotFoundException("Veículo", vehicle.getId());
            }
            mapper.applyState(entity, vehicle);
        }
        repository.flush();
        return mapper.toDomain(entity);
    }

    @Override
    public long countAll() {
        return repository.count();
    }

    @Override
    public void removeById(Long id) {
        repository.deleteById(id);
    }
}
