package br.com.oficina.application.dto;

import br.com.oficina.domain.model.WorkOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PublicWorkOrderDto(
    String orderNumber,
    WorkOrderStatus status,
    String vehicleLicensePlate,
    String vehicleBrand,
    String vehicleModel,
    BigDecimal totalCost,
    LocalDateTime createdAt,
    LocalDateTime sentForApprovalAt,
    LocalDateTime approvedAt,
    LocalDateTime finishedAt,
    LocalDateTime deliveredAt,
    List<String> serviceNames,
    List<String> partNames
) {
    public static PublicWorkOrderDto from(WorkOrderResponseDto dto) {
        return new PublicWorkOrderDto(
            dto.orderNumber(),
            dto.status(),
            dto.vehicleLicensePlate(),
            dto.vehicleBrand(),
            dto.vehicleModel(),
            dto.totalCost(),
            dto.createdAt(),
            dto.sentForApprovalAt(),
            dto.approvedAt(),
            dto.finishedAt(),
            dto.deliveredAt(),
            dto.services().stream()
                .map(WorkOrderResponseDto.WorkOrderServiceSummaryDto::serviceName)
                .toList(),
            dto.parts().stream()
                .map(WorkOrderResponseDto.WorkOrderPartSummaryDto::partName)
                .toList()
        );
    }
}
