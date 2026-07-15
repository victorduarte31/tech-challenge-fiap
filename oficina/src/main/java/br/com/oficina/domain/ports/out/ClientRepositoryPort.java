package br.com.oficina.domain.ports.out;

import br.com.oficina.domain.model.Client;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saída (driven port) para persistência do aggregate {@code Client}.
 * Definida no domínio; implementada por um adapter na infraestrutura.
 */
public interface ClientRepositoryPort {

    List<Client> listAll(int page, int size);

    Optional<Client> fetchById(Long id);

    Optional<Client> findByCpfCnpj(String cpfCnpj);

    boolean existsByCpfCnpj(String cpfCnpj);

    /** Upsert: persiste/atualiza e devolve o domínio remapeado (id e timestamps populados). */
    Client save(Client client);

    void removeById(Long id);
}
