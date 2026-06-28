package br.com.oficina.application.ports.in;

import br.com.oficina.application.dto.WorkOrderPartDto;
import br.com.oficina.application.dto.WorkOrderResponseDto;
import br.com.oficina.application.dto.WorkOrderServiceDto;

/**
 * Porta de entrada (driver port) para gestão dos itens (serviços e peças) de uma
 * OS editável. A inclusão de peças coordena a baixa de estoque; a remoção,
 * a devolução (regra cross-aggregate na camada de aplicação).
 */
public interface ManageWorkOrderItemsUseCase {

    WorkOrderResponseDto addService(Long id, WorkOrderServiceDto dto);

    WorkOrderResponseDto removeService(Long id, Long serviceLineId);

    WorkOrderResponseDto addPart(Long id, WorkOrderPartDto dto);

    WorkOrderResponseDto removePart(Long id, Long partLineId);
}
