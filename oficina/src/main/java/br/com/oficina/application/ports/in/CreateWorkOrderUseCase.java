package br.com.oficina.application.ports.in;

import br.com.oficina.application.dto.WorkOrderCreateDto;
import br.com.oficina.application.dto.WorkOrderResponseDto;

/**
 * Porta de entrada (driver port) para abertura de Ordem de Serviço, incluindo o
 * registro inicial de serviços e peças informados na criação.
 */
public interface CreateWorkOrderUseCase {

    WorkOrderResponseDto create(WorkOrderCreateDto dto);
}
