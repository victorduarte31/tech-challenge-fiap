package br.com.oficina.domain.ports.out;

import br.com.oficina.domain.model.ServiceItem;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saída (driven port) para persistência do aggregate {@code ServiceItem}.
 * Definida no domínio; implementada por um adapter na infraestrutura.
 */
public interface ServiceItemRepositoryPort {

    List<ServiceItem> listAllItems();

    List<ServiceItem> findAllActive();

    Optional<ServiceItem> fetchById(Long id);

    /** Upsert: persiste/atualiza e devolve o domínio remapeado (id e timestamps populados). */
    ServiceItem save(ServiceItem item);

    void removeById(Long id);
}
