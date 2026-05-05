package br.com.oficina.infrastructure.repository;

import br.com.oficina.domain.model.Client;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class ClientRepository implements PanacheRepository<Client> {

    public Optional<Client> findByCpfCnpj(String cpfCnpj) {
        return find("cpfCnpj", cpfCnpj).firstResultOptional();
    }

    public boolean existsByCpfCnpj(String cpfCnpj) {
        return count("cpfCnpj", cpfCnpj) > 0;
    }
}
