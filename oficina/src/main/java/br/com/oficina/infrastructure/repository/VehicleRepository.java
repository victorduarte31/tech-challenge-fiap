package br.com.oficina.infrastructure.repository;

import br.com.oficina.domain.model.Vehicle;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class VehicleRepository implements PanacheRepository<Vehicle> {

    public List<Vehicle> listAll(int page, int size) {
        return findAll(Sort.by("id")).page(Page.of(page, size)).list();
    }

    public List<Vehicle> findByClientId(Long clientId) {
        return list("client.id", clientId);
    }

    public boolean existsByLicensePlate(String licensePlate) {
        return count("licensePlate", licensePlate) > 0;
    }
}
