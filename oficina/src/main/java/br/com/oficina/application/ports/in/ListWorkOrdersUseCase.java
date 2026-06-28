package br.com.oficina.application.ports.in;

import br.com.oficina.application.dto.WorkOrderResponseDto;
import br.com.oficina.domain.model.WorkOrderStatus;
import java.util.List;

/**
 * Porta de entrada (driver port) para consultas de Ordens de Serviço.
 */
public interface ListWorkOrdersUseCase {

    /** Lista as OS ativas, ordenadas por prioridade de status e antiguidade. */
    List<WorkOrderResponseDto> listActive(int page, int size);

    List<WorkOrderResponseDto> listByStatus(WorkOrderStatus status, int page, int size);

    WorkOrderResponseDto findById(Long id);

    WorkOrderResponseDto findByOrderNumber(String orderNumber);
}
