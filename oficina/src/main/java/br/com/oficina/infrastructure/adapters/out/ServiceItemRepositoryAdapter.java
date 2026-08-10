package br.com.oficina.infrastructure.adapters.out;

import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.ServiceItem;
import br.com.oficina.application.ports.out.ServiceItemRepositoryPort;
import br.com.oficina.infrastructure.persistence.ServiceItemEntity;
import br.com.oficina.infrastructure.persistence.ServiceItemMapper;
import br.com.oficina.infrastructure.persistence.ServiceItemPanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

/**
 * Adapter de persistência do aggregate {@code ServiceItem}. Opera sobre
 * {@code ServiceItemEntity} via Panache (por composição) e expõe à aplicação apenas o domínio puro,
 * traduzido pelo {@link ServiceItemMapper}.
 */
@ApplicationScoped
public class ServiceItemRepositoryAdapter implements ServiceItemRepositoryPort {

    @Inject
    ServiceItemMapper mapper;

    @Inject
    ServiceItemPanacheRepository repository;

    @Override
    public List<ServiceItem> listAllItems() {
        return repository.listAll().stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public List<ServiceItem> findAllActive() {
        return repository.list("active", true).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public Optional<ServiceItem> fetchById(Long id) {
        return repository.findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public ServiceItem save(ServiceItem item) {
        ServiceItemEntity entity;
        if (item.getId() == null) {
            entity = mapper.toNewEntity(item);
            repository.persist(entity);
        } else {
            entity = repository.findById(item.getId());
            if (entity == null) {
                throw new ResourceNotFoundException("Serviço", item.getId());
            }
            mapper.applyState(entity, item);
        }
        repository.flush();
        return mapper.toDomain(entity);
    }

    @Override
    public void removeById(Long id) {
        repository.deleteById(id);
    }
}
