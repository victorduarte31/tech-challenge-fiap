package br.com.oficina.infrastructure.adapters.out;

import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.Part;
import br.com.oficina.domain.ports.out.PartRepositoryPort;
import br.com.oficina.infrastructure.persistence.PartEntity;
import br.com.oficina.infrastructure.persistence.PartMapper;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

/**
 * Adapter de persistência do aggregate {@code Part}. Opera sobre {@code PartEntity}
 * via Panache e expõe à aplicação apenas o domínio puro, traduzido pelo
 * {@link PartMapper}. O lock otimista ({@code @Version}) é gerenciado na entidade.
 */
@ApplicationScoped
public class PartRepositoryAdapter implements PartRepositoryPort, PanacheRepository<PartEntity> {

    @Inject
    PartMapper mapper;

    @Override
    public List<Part> listActive(int page, int size) {
        return find("active = true", Sort.by("id")).page(Page.of(page, size)).list().stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public Optional<Part> fetchById(Long id) {
        return findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public List<Part> findLowStock() {
        return list("active = true and stockQuantity <= minimumStock").stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public long countLowStock() {
        return count("active = true and stockQuantity <= minimumStock");
    }

    @Override
    public Part save(Part part) {
        PartEntity entity;
        if (part.getId() == null) {
            entity = mapper.toNewEntity(part);
            persist(entity);
        } else {
            entity = findById(part.getId());
            if (entity == null) {
                throw new ResourceNotFoundException("Peça/Insumo", part.getId());
            }
            mapper.applyState(entity, part);
        }
        flush();
        return mapper.toDomain(entity);
    }
}
