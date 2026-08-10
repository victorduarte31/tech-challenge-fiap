package br.com.oficina.application.ports.out;

import br.com.oficina.domain.model.Client;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saída (driven port) para persistência do aggregate {@code Client}.
 * Declarada na camada de aplicação (que a consome) e implementada por um adapter
 * na infraestrutura — a dependência aponta de fora para dentro.
 */
public interface ClientRepositoryPort {

    List<Client> listAll(int page, int size);

    Optional<Client> fetchById(Long id);

    Optional<Client> findByCpfCnpj(String cpfCnpj);

    boolean existsByCpfCnpj(String cpfCnpj);

    /** Upsert: persiste/atualiza e devolve o domínio remapeado (id e timestamps populados). */
    Client save(Client client);

    /** Total de registros, para o cabeçalho X-Total-Count da listagem. */
    long countAll();

    void removeById(Long id);
}
