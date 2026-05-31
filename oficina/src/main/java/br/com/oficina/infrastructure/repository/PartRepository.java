package br.com.oficina.infrastructure.repository;

import br.com.oficina.domain.model.Part;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class PartRepository implements PanacheRepository<Part> {

    /**
     * Peças cujo estoque atual está igual ou abaixo do mínimo definido para cada peça.
     */
    public List<Part> findLowStock() {
        return list("stockQuantity <= minimumStock");
    }
}
