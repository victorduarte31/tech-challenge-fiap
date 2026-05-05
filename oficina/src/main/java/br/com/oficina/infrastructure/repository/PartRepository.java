package br.com.oficina.infrastructure.repository;

import br.com.oficina.domain.model.Part;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class PartRepository implements PanacheRepository<Part> {

    public List<Part> findLowStock(int threshold) {
        return list("stockQuantity <= ?1", threshold);
    }
}
