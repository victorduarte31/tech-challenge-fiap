package br.com.oficina.infrastructure.repository;

import br.com.oficina.domain.model.Part;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class PartRepository implements PanacheRepository<Part> {

    /** Catálogo paginado de peças ativas (soft-delete: peças inativas não aparecem). */
    public List<Part> listActive(int page, int size) {
        return find("active = true", Sort.by("id")).page(Page.of(page, size)).list();
    }

    /**
     * Peças ativas cujo estoque atual está igual ou abaixo do mínimo definido para cada peça.
     */
    public List<Part> findLowStock() {
        return list("active = true and stockQuantity <= minimumStock");
    }

    /** Contagem (no banco) de peças ativas com estoque igual ou abaixo do mínimo. */
    public long countLowStock() {
        return count("active = true and stockQuantity <= minimumStock");
    }
}
