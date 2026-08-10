package br.com.oficina.infrastructure.adapters.out;

import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.Client;
import br.com.oficina.application.ports.out.ClientRepositoryPort;
import br.com.oficina.infrastructure.persistence.ClientEntity;
import br.com.oficina.infrastructure.persistence.ClientMapper;
import br.com.oficina.infrastructure.persistence.ClientPanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

/**
 * Adapter de persistência do aggregate {@code Client}. Opera sobre
 * {@code ClientEntity} via Panache (por composição) e expõe à aplicação apenas o domínio puro,
 * traduzido pelo {@link ClientMapper}. Substitui a dependência implícita do
 * dirty-checking por persistência explícita ({@link #save(Client)}).
 */
@ApplicationScoped
public class ClientRepositoryAdapter implements ClientRepositoryPort {

    @Inject
    ClientMapper mapper;

    @Inject
    ClientPanacheRepository repository;

    @Override
    public List<Client> listAll(int page, int size) {
        return repository.findAll(Sort.by("id")).page(Page.of(page, size)).list().stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public Optional<Client> fetchById(Long id) {
        return repository.findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Client> findByCpfCnpj(String cpfCnpj) {
        return repository.find("cpfCnpj", cpfCnpj).firstResultOptional().map(mapper::toDomain);
    }

    @Override
    public boolean existsByCpfCnpj(String cpfCnpj) {
        return repository.count("cpfCnpj", cpfCnpj) > 0;
    }

    @Override
    public Client save(Client client) {
        ClientEntity entity;
        if (client.getId() == null) {
            entity = mapper.toNewEntity(client);
            repository.persist(entity);
        } else {
            entity = repository.findById(client.getId());
            if (entity == null) {
                throw new ResourceNotFoundException("Cliente", client.getId());
            }
            mapper.applyState(entity, client);
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
