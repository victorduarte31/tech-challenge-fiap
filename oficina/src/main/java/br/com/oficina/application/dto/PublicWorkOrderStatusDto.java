package br.com.oficina.application.dto;

import br.com.oficina.domain.model.WorkOrderStatus;

import java.time.LocalDateTime;

/**
 * Visão pública e mínima do acompanhamento da OS, devolvida pelo endpoint aberto
 * {@code GET /public/work-orders/{orderNumber}/status}.
 */
public record PublicWorkOrderStatusDto(
    String orderNumber,
    WorkOrderStatus status,
    String statusLabel,
    LocalDateTime createdAt,
    LocalDateTime sentForApprovalAt,
    LocalDateTime approvedAt,
    LocalDateTime finishedAt,
    LocalDateTime deliveredAt
) {
    public static PublicWorkOrderStatusDto from(WorkOrderResponseDto dto) {
        return new PublicWorkOrderStatusDto(
            dto.orderNumber(),
            dto.status(),
            WorkOrderStatusLabel.of(dto.status()),
            dto.createdAt(),
            dto.sentForApprovalAt(),
            dto.approvedAt(),
            dto.finishedAt(),
            dto.deliveredAt()
        );
    }
}
