package br.com.oficina.infrastructure.repository;

import br.com.oficina.domain.model.WorkOrder;
import br.com.oficina.domain.model.WorkOrderStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class WorkOrderRepository implements PanacheRepository<WorkOrder> {

    public Optional<WorkOrder> findByOrderNumber(String orderNumber) {
        return find("orderNumber", orderNumber).firstResultOptional();
    }

    public List<WorkOrder> findByStatus(WorkOrderStatus status) {
        return list("status", status);
    }

}
