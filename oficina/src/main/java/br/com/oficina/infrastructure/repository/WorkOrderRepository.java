package br.com.oficina.infrastructure.repository;

import br.com.oficina.domain.model.WorkOrder;
import br.com.oficina.domain.model.WorkOrderStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
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

    public List<WorkOrder> listAll(int page, int size) {
        return findAll(Sort.by("id")).page(Page.of(page, size)).list();
    }

    public List<WorkOrder> findByStatus(WorkOrderStatus status, int page, int size) {
        return find("status", Sort.by("id"), status).page(Page.of(page, size)).list();
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

    /**
     * Projeção escalar (executionStartedAt, finishedAt) das OS com tempo de execução
     * registrado. Evita materializar o agregado WorkOrder e suas associações EAGER
     * (Client/Vehicle) apenas para o cálculo da média de tempo de execução.
     */
    public List<Object[]> findExecutionTimestamps() {
        return em.createQuery(
            "SELECT w.executionStartedAt, w.finishedAt FROM WorkOrder w " +
            "WHERE w.executionStartedAt IS NOT NULL AND w.finishedAt IS NOT NULL", Object[].class)
            .getResultList();
    }
}
