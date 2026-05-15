package br.com.oficina.infrastructure.repository;

import br.com.oficina.domain.model.WorkOrder;
import br.com.oficina.domain.model.WorkOrderStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class WorkOrderRepository implements PanacheRepository<WorkOrder> {

    @Inject
    EntityManager em;

    public Optional<WorkOrder> findByOrderNumber(String orderNumber) {
        return find("orderNumber", orderNumber).firstResultOptional();
    }

    public List<WorkOrder> findByStatus(WorkOrderStatus status) {
        return list("status", status);
    }

    public long countOpen() {
        return count("status not in (?1, ?2, ?3)",
            WorkOrderStatus.FINISHED, WorkOrderStatus.DELIVERED, WorkOrderStatus.CANCELLED);
    }

    public long countFinished() {
        return count("status in (?1, ?2)",
            WorkOrderStatus.FINISHED, WorkOrderStatus.DELIVERED);
    }

    public long countCancelled() {
        return count("status", WorkOrderStatus.CANCELLED);
    }

    public BigDecimal sumRevenueDelivered() {
        Object result = em.createQuery(
            "SELECT COALESCE(SUM(w.totalCost), 0) FROM WorkOrder w WHERE w.status = :status")
            .setParameter("status", WorkOrderStatus.DELIVERED)
            .getSingleResult();
        return new BigDecimal(result.toString());
    }

    /** Retorna apenas as OS que têm tempo de execução registrado (para cálculo in-memory). */
    public List<WorkOrder> findWithExecutionTime() {
        return list("executionStartedAt IS NOT NULL AND finishedAt IS NOT NULL");
    }
}
