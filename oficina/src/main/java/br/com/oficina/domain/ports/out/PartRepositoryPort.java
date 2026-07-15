package br.com.oficina.domain.ports.out;

import br.com.oficina.domain.model.Part;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saída (driven port) para persistência do aggregate {@code Part}.
 * Definida no domínio; implementada por um adapter na infraestrutura.
 */
public interface PartRepositoryPort {

    List<Part> listActive(int page, int size);

    Optional<Part> fetchById(Long id);

    /** Peças ativas com estoque igual ou abaixo do mínimo (alerta de reposição). */
    List<Part> findLowStock();

    long countLowStock();

    /** Upsert: persiste/atualiza e devolve o domínio remapeado (id e timestamps populados). */
    Part save(Part part);
}
