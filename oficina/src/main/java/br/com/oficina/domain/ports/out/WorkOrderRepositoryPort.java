package br.com.oficina.domain.ports.out;

import br.com.oficina.domain.model.WorkOrder;
import br.com.oficina.domain.model.WorkOrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saída (driven port) para persistência do aggregate {@code WorkOrder}.
 * Definida no domínio; implementada por um adapter na infraestrutura. Inclui as
 * consultas agregadas usadas pelos indicadores (métricas), expostas em tipos de
 * domínio para não vazar detalhes de persistência.
 */
public interface WorkOrderRepositoryPort {

    /** Upsert: persiste/atualiza e devolve o aggregate remapeado (ids e timestamps populados). */
    WorkOrder save(WorkOrder workOrder);

    Optional<WorkOrder> fetchById(Long id);

    Optional<WorkOrder> findByOrderNumber(String orderNumber);

    /**
     * Lista as OS ativas (exclui logicamente {@code FINISHED}/{@code DELIVERED}),
     * ordenadas por prioridade de status
     * ({@code IN_EXECUTION > AWAITING_APPROVAL > IN_DIAGNOSIS > RECEIVED}) e, dentro
     * do mesmo status, das mais antigas para as mais recentes ({@code createdAt ASC}).
     */
    List<WorkOrder> findActive(int page, int size);

    List<WorkOrder> findByStatus(WorkOrderStatus status, int page, int size);

    long countAll();

    long countOpen();

    long countFinished();

    long countCancelled();

    BigDecimal sumRevenueDelivered();

    /** Média (em minutos) do tempo de execução das OS com início e fim registrados. */
    double averageExecutionTimeMinutes();
}
