package br.com.oficina.infrastructure.adapters.out;

import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.WorkOrder;
import br.com.oficina.domain.model.WorkOrderStatus;
import br.com.oficina.domain.ports.out.WorkOrderRepositoryPort;
import br.com.oficina.infrastructure.persistence.WorkOrderEntity;
import br.com.oficina.infrastructure.persistence.WorkOrderMapper;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Adapter de persistência do aggregate {@code WorkOrder}. Opera sobre
 * {@code WorkOrderEntity} via Panache, mas expõe à aplicação apenas o aggregate de
 * domínio puro {@code WorkOrder}, traduzido pelo {@link WorkOrderMapper}. Substitui
 * a dependência implícita do dirty-checking por persistência explícita
 * ({@link #save(WorkOrder)}).
 */
@ApplicationScoped
public class WorkOrderRepositoryAdapter implements WorkOrderRepositoryPort, PanacheRepository<WorkOrderEntity> {

    private static final String ACTIVE_ORDER_QUERY =
        "status not in (?1, ?2) " +
        "order by case status " +
        "when ?3 then 0 when ?4 then 1 when ?5 then 2 when ?6 then 3 else 4 end, createdAt asc";

    @Inject
    EntityManager em;

    @Inject
    WorkOrderMapper mapper;

    @Override
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

    @Override
    public Optional<WorkOrder> fetchById(Long id) {
        return findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public Optional<WorkOrder> findByOrderNumber(String orderNumber) {
        return find("orderNumber", orderNumber).firstResultOptional().map(mapper::toDomain);
    }

    /**
     * Ordenação por prioridade de status via {@code CASE} e antiguidade
     * ({@code createdAt ASC}), excluindo logicamente OS já encerradas
     * ({@code FINISHED}/{@code DELIVERED}). A prioridade é uma regra de exibição
     * (infraestrutura de consulta), por isso vive no adapter e não no domínio.
     */
    @Override
    public List<WorkOrder> findActive(int page, int size) {
        return find(ACTIVE_ORDER_QUERY,
                WorkOrderStatus.FINISHED, WorkOrderStatus.DELIVERED,
                WorkOrderStatus.IN_EXECUTION, WorkOrderStatus.AWAITING_APPROVAL,
                WorkOrderStatus.IN_DIAGNOSIS, WorkOrderStatus.RECEIVED)
            .page(Page.of(page, size)).list().stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public List<WorkOrder> findByStatus(WorkOrderStatus status, int page, int size) {
        return find("status", Sort.by("id"), status).page(Page.of(page, size)).list().stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public long countAll() {
        return count();
    }

    @Override
    public long countOpen() {
        return count("status not in (?1, ?2, ?3)",
            WorkOrderStatus.FINISHED, WorkOrderStatus.DELIVERED, WorkOrderStatus.CANCELLED);
    }

    @Override
    public long countFinished() {
        return count("status in (?1, ?2)",
            WorkOrderStatus.FINISHED, WorkOrderStatus.DELIVERED);
    }

    @Override
    public long countCancelled() {
        return count("status", WorkOrderStatus.CANCELLED);
    }

    @Override
    public BigDecimal sumRevenueDelivered() {
        Object result = em.createQuery(
            "SELECT COALESCE(SUM(w.totalCost), 0) FROM WorkOrderEntity w WHERE w.status = :status")
            .setParameter("status", WorkOrderStatus.DELIVERED)
            .getSingleResult();
        return new BigDecimal(result.toString());
    }

    /**
     * Média (em minutos) do tempo de execução das OS com {@code executionStartedAt} e
     * {@code finishedAt} registrados. Usa projeção escalar para evitar materializar o
     * aggregate e suas associações EAGER apenas para o cálculo.
     */
    @Override
    public double averageExecutionTimeMinutes() {
        List<Object[]> rows = em.createQuery(
            "SELECT w.executionStartedAt, w.finishedAt FROM WorkOrderEntity w " +
            "WHERE w.executionStartedAt IS NOT NULL AND w.finishedAt IS NOT NULL", Object[].class)
            .getResultList();

        return rows.stream()
            .mapToLong(row -> Duration.between((LocalDateTime) row[0], (LocalDateTime) row[1]).toMinutes())
            .average()
            .orElse(0.0);
    }
}
