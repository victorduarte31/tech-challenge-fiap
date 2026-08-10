package br.com.oficina.infrastructure.adapters.out;

import br.com.oficina.application.ports.out.WorkOrderRepositoryPort;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.WorkOrder;
import br.com.oficina.domain.model.WorkOrderStatus;
import br.com.oficina.infrastructure.persistence.WorkOrderEntity;
import br.com.oficina.infrastructure.persistence.WorkOrderMapper;
import br.com.oficina.infrastructure.persistence.WorkOrderPanacheRepository;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Adapter de persistência do aggregate {@code WorkOrder}. Opera sobre
 * {@code WorkOrderEntity} via Panache (por composição, não por herança) e expõe à
 * aplicação apenas o aggregate de domínio puro, traduzido pelo
 * {@link WorkOrderMapper}. Substitui a dependência implícita do dirty-checking
 * por persistência explícita ({@link #save(WorkOrder)}).
 */
@ApplicationScoped
public class WorkOrderRepositoryAdapter implements WorkOrderRepositoryPort {

    private static final String ACTIVE_FILTER = "status not in ?1";

    @Inject
    EntityManager em;

    @Inject
    WorkOrderMapper mapper;

    @Inject
    WorkOrderPanacheRepository repository;

    @Override
    public WorkOrder save(WorkOrder workOrder) {
        WorkOrderEntity entity;
        if (workOrder.getId() == null) {
            entity = mapper.toNewEntity(workOrder);
            repository.persist(entity);
        } else {
            entity = repository.findById(workOrder.getId());
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
        return repository.findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public Optional<WorkOrder> findByOrderNumber(String orderNumber) {
        return repository.find("orderNumber", orderNumber).firstResultOptional().map(mapper::toDomain);
    }

    /**
     * Exclui os estados terminais e ordena pela prioridade declarada no domínio
     * ({@link WorkOrderStatus#activeByPriority()}), com desempate por antiguidade
     * ({@code createdAt ASC}). A cláusula {@code CASE} é derivada do enum em vez de
     * escrita à mão: incluir um novo status ativo passa a exigir apenas a
     * declaração da prioridade no domínio, sem tocar nesta consulta.
     */
    @Override
    public List<WorkOrder> findActive(int page, int size) {
        return repository.find(activeOrderedQuery(), WorkOrderStatus.terminalStatuses())
            .page(Page.of(page, size)).list().stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public List<WorkOrder> findByStatus(WorkOrderStatus status, int page, int size) {
        return repository.find("status", Sort.by("id"), status).page(Page.of(page, size)).list().stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public long countActive() {
        return repository.count(ACTIVE_FILTER, WorkOrderStatus.terminalStatuses());
    }

    @Override
    public long countByStatus(WorkOrderStatus status) {
        return repository.count("status", status);
    }

    @Override
    public long countAll() {
        return repository.count();
    }

    @Override
    public long countOpen() {
        return repository.count(ACTIVE_FILTER, WorkOrderStatus.terminalStatuses());
    }

    @Override
    public long countFinished() {
        return repository.count("status in (?1, ?2)",
            WorkOrderStatus.FINISHED, WorkOrderStatus.DELIVERED);
    }

    @Override
    public long countCancelled() {
        return repository.count("status", WorkOrderStatus.CANCELLED);
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

    /**
     * Monta {@code status not in ?1 order by case status when ... then N end,
     * createdAt asc} a partir da prioridade declarada no enum de domínio. Os nomes
     * das constantes são interpolados literalmente — são valores do próprio enum,
     * nunca entrada de usuário, então não há superfície de injeção.
     */
    private static String activeOrderedQuery() {
        List<WorkOrderStatus> active = WorkOrderStatus.activeByPriority();
        String cases = IntStream.range(0, active.size())
            .mapToObj(i -> "when br.com.oficina.domain.model.WorkOrderStatus." + active.get(i).name()
                           + " then " + i)
            .collect(Collectors.joining(" "));
        return ACTIVE_FILTER + " order by case status " + cases + " else " + active.size()
               + " end, createdAt asc";
    }
}
