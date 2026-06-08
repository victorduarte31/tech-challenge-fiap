package br.com.oficina.infrastructure.repository;

import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.WorkOrder;
import br.com.oficina.domain.model.WorkOrderStatus;
import br.com.oficina.infrastructure.persistence.WorkOrderEntity;
import br.com.oficina.infrastructure.persistence.WorkOrderMapper;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Adapter de persistência da OS. Opera sobre {@code WorkOrderEntity} via Panache,
 * mas expõe à aplicação apenas o aggregate de domínio puro {@code WorkOrder},
 * traduzido pelo {@link WorkOrderMapper}. Substitui a dependência implícita do
 * dirty-checking por persistência explícita ({@link #save(WorkOrder)}).
 */
@ApplicationScoped
public class WorkOrderRepository implements PanacheRepository<WorkOrderEntity> {

    @Inject
    EntityManager em;

    @Inject
    WorkOrderMapper mapper;

    /** Upsert: persiste/atualiza e devolve o aggregate remapeado (ids e timestamps populados). */
    public WorkOrder save(WorkOrder workOrder) {
        WorkOrderEntity entity;
        if (workOrder.getId() == null) {
            entity = mapper.toNewEntity(workOrder);
            persist(entity);
        } else {
            entity = findById(workOrder.getId());
            if (entity == null) {
                throw new ResourceNotFoundException("Ordem de Serviço", workOrder.getId());
            }
            mapper.applyState(entity, workOrder);
        }
        em.flush();
        return mapper.toDomain(entity);
    }

    public Optional<WorkOrder> fetchById(Long id) {
        return findByIdOptional(id).map(mapper::toDomain);
    }

    public Optional<WorkOrder> findByOrderNumber(String orderNumber) {
        return find("orderNumber", orderNumber).firstResultOptional().map(mapper::toDomain);
    }

    public List<WorkOrder> listAll(int page, int size) {
        return findAll(Sort.by("id")).page(Page.of(page, size)).list().stream()
            .map(mapper::toDomain)
            .toList();
    }

    public List<WorkOrder> findByStatus(WorkOrderStatus status, int page, int size) {
        return find("status", Sort.by("id"), status).page(Page.of(page, size)).list().stream()
            .map(mapper::toDomain)
            .toList();
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
            "SELECT COALESCE(SUM(w.totalCost), 0) FROM WorkOrderEntity w WHERE w.status = :status")
            .setParameter("status", WorkOrderStatus.DELIVERED)
            .getSingleResult();
        return new BigDecimal(result.toString());
    }

    /**
     * Projeção escalar (executionStartedAt, finishedAt) das OS com tempo de execução
     * registrado. Evita materializar o agregado e suas associações EAGER
     * (Client/Vehicle) apenas para o cálculo da média de tempo de execução.
     */
    public List<Object[]> findExecutionTimestamps() {
        return em.createQuery(
            "SELECT w.executionStartedAt, w.finishedAt FROM WorkOrderEntity w " +
            "WHERE w.executionStartedAt IS NOT NULL AND w.finishedAt IS NOT NULL", Object[].class)
            .getResultList();
    }
}
