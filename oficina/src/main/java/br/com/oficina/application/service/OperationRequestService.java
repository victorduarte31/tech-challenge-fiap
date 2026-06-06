package br.com.oficina.application.service;

import br.com.oficina.application.dto.CancellationRequestDto;
import br.com.oficina.application.dto.OperationRequestResponseDto;
import br.com.oficina.application.dto.StockAdjustmentRequestDto;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.*;
import br.com.oficina.infrastructure.repository.OperationRequestRepository;
import br.com.oficina.infrastructure.repository.PartRepository;
import br.com.oficina.infrastructure.repository.WorkOrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import java.util.List;

/**
 * Fluxo maker-checker: a atendente solicita operações sensíveis (ajuste de
 * estoque, cancelamento de OS) e o dono (ADMIN) aprova — momento em que a
 * operação é de fato executada — ou rejeita.
 */
@ApplicationScoped
public class OperationRequestService {

    OperationRequestRepository requestRepository;
    PartRepository partRepository;
    WorkOrderRepository workOrderRepository;
    PartService partService;
    WorkOrderService workOrderService;

    public OperationRequestService(OperationRequestRepository requestRepository,
                                   PartRepository partRepository,
                                   WorkOrderRepository workOrderRepository,
                                   PartService partService,
                                   WorkOrderService workOrderService) {
        this.requestRepository = requestRepository;
        this.partRepository = partRepository;
        this.workOrderRepository = workOrderRepository;
        this.partService = partService;
        this.workOrderService = workOrderService;
    }

    @Transactional
    public OperationRequestResponseDto requestStockAdjustment(StockAdjustmentRequestDto dto, String requestedBy) {
        if (dto.adjustment() == 0) {
            throw new BusinessException("O ajuste deve ser diferente de zero.");
        }
        Part part = partRepository.findByIdOptional(dto.partId())
            .orElseThrow(() -> new ResourceNotFoundException("Peça/Insumo", dto.partId()));

        OperationRequest req = new OperationRequest(
            RequestType.STOCK_ADJUSTMENT, part.getId(), part.getName(),
            dto.adjustment(), dto.reason(), requestedBy);
        requestRepository.persist(req);
        return OperationRequestResponseDto.from(req);
    }

    @Transactional
    public OperationRequestResponseDto requestCancellation(CancellationRequestDto dto, String requestedBy) {
        WorkOrder wo = workOrderRepository.findByIdOptional(dto.workOrderId())
            .orElseThrow(() -> new ResourceNotFoundException("Ordem de Serviço", dto.workOrderId()));

        if (wo.getStatus() == WorkOrderStatus.DELIVERED || wo.getStatus() == WorkOrderStatus.CANCELLED) {
            throw new BusinessException("Não é possível solicitar cancelamento de uma OS com status: " + wo.getStatus());
        }

        OperationRequest req = new OperationRequest(
            RequestType.WORK_ORDER_CANCELLATION, wo.getId(), "OS-" + wo.getOrderNumber(),
            null, dto.reason(), requestedBy);
        requestRepository.persist(req);
        return OperationRequestResponseDto.from(req);
    }

    @Transactional(TxType.SUPPORTS)
    public List<OperationRequestResponseDto> list(RequestStatus status, int page, int size) {
        var list = (status != null)
            ? requestRepository.listByStatus(status, page, size)
            : requestRepository.listAll(page, size);
        return list.stream().map(OperationRequestResponseDto::from).toList();
    }

    @Transactional(TxType.SUPPORTS)
    public long countPending() {
        return requestRepository.countByStatus(RequestStatus.PENDING);
    }

    @Transactional
    public OperationRequestResponseDto approve(Long requestId, String decidedBy) {
        OperationRequest req = requestRepository.findByIdOptional(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("Solicitação", requestId));

        // Executa a operação subjacente; se falhar (estoque insuficiente, OS já
        // cancelada, etc.) a transação inteira sofre rollback e a solicitação
        // permanece PENDING.
        switch (req.getType()) {
            case STOCK_ADJUSTMENT -> partService.adjustStock(req.getTargetId(), req.getAdjustment());
            case WORK_ORDER_CANCELLATION -> workOrderService.cancel(req.getTargetId());
        }

        req.approve(decidedBy);
        return OperationRequestResponseDto.from(req);
    }

    @Transactional
    public OperationRequestResponseDto reject(Long requestId, String decidedBy, String note) {
        OperationRequest req = requestRepository.findByIdOptional(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("Solicitação", requestId));
        req.reject(decidedBy, note);
        return OperationRequestResponseDto.from(req);
    }
}
