package br.com.oficina.application.ports.out;

import br.com.oficina.domain.model.Part;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saída (driven port) para persistência do aggregate {@code Part}.
 * Declarada na camada de aplicação (que a consome) e implementada por um adapter
 * na infraestrutura — a dependência aponta de fora para dentro.
 */
public interface PartRepositoryPort {

    List<Part> listActive(int page, int size);

    Optional<Part> fetchById(Long id);

    /** Peças ativas com estoque igual ou abaixo do mínimo (alerta de reposição). */
    List<Part> findLowStock();

    /** Total de peças ativas, para o cabeçalho X-Total-Count da listagem. */
    long countActive();

    long countLowStock();

    /** Upsert: persiste/atualiza e devolve o domínio remapeado (id e timestamps populados). */
    Part save(Part part);
}
