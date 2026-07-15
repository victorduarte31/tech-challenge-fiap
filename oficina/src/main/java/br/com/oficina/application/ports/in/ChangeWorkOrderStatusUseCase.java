package br.com.oficina.application.ports.in;

import br.com.oficina.application.dto.WorkOrderResponseDto;

/**
 * Porta de entrada (driver port) para as transições de estado da OS conduzidas
 * internamente pela oficina (máquina de estados do aggregate {@code WorkOrder}).
 */
public interface ChangeWorkOrderStatusUseCase {

    WorkOrderResponseDto startDiagnosis(Long id);

    WorkOrderResponseDto sendForApproval(Long id);

    WorkOrderResponseDto approve(Long id);

    WorkOrderResponseDto reject(Long id);

    WorkOrderResponseDto complete(Long id);

    WorkOrderResponseDto deliver(Long id);

    WorkOrderResponseDto cancel(Long id);
}
