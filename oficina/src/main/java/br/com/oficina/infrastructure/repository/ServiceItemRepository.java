package br.com.oficina.infrastructure.repository;

import br.com.oficina.domain.model.ServiceItem;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class ServiceItemRepository implements PanacheRepository<ServiceItem> {

    public List<ServiceItem> findAllActive() {
        return list("active", true);
    }

}
