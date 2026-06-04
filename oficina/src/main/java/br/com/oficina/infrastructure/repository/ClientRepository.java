package br.com.oficina.infrastructure.repository;

import br.com.oficina.domain.model.Client;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ClientRepository implements PanacheRepository<Client> {

    public List<Client> listAll(int page, int size) {
        return findAll(Sort.by("id")).page(Page.of(page, size)).list();
    }

    public Optional<Client> findByCpfCnpj(String cpfCnpj) {
        return find("cpfCnpj", cpfCnpj).firstResultOptional();
    }

    public boolean existsByCpfCnpj(String cpfCnpj) {
        return count("cpfCnpj", cpfCnpj) > 0;
    }
}
