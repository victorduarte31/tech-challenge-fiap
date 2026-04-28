package br.com.oficina.application.dto;

import br.com.oficina.domain.model.WorkOrder;
import br.com.oficina.domain.model.WorkOrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record WorkOrderResponseDto(
    Long id,
    String orderNumber,
    WorkOrderStatus status,
    String clientName,
    String clientCpfCnpj,
    String vehicleLicensePlate,
    String vehicleBrand,
    String vehicleModel,
    Integer vehicleYear,
    String notes,
    BigDecimal totalCost,
    LocalDateTime createdAt,
    LocalDateTime diagnosisStartedAt,
    LocalDateTime sentForApprovalAt,
    LocalDateTime approvedAt,
    LocalDateTime executionStartedAt,
    LocalDateTime finishedAt,
    LocalDateTime deliveredAt,
    LocalDateTime cancelledAt,
    List<WorkOrderPartSummaryDto> parts,
    List<WorkOrderServiceSummaryDto> services
) {
    public record WorkOrderPartSummaryDto(
        Long id, String partName, Integer quantity, BigDecimal unitPrice, BigDecimal subtotal
    ) {}

    public record WorkOrderServiceSummaryDto(
        Long id, String serviceName, BigDecimal price, String notes
    ) {}

    public static WorkOrderResponseDto from(WorkOrder wo) {
        List<WorkOrderPartSummaryDto> partDtos = wo.parts.stream()
            .map(p -> new WorkOrderPartSummaryDto(
                p.id, p.part.name, p.quantity, p.unitPrice, p.getSubtotal()
            ))
            .toList();

        List<WorkOrderServiceSummaryDto> serviceDtos = wo.services.stream()
            .map(s -> new WorkOrderServiceSummaryDto(
                s.id, s.serviceItem.name, s.price, s.notes
            ))
            .toList();

        return new WorkOrderResponseDto(
            wo.id, wo.orderNumber, wo.status,
            wo.client.name, wo.client.cpfCnpj,
            wo.vehicle.licensePlate, wo.vehicle.brand, wo.vehicle.model, wo.vehicle.productionYear,
            wo.notes, wo.totalCost,
            wo.createdAt, wo.diagnosisStartedAt, wo.sentForApprovalAt,
            wo.approvedAt, wo.executionStartedAt, wo.finishedAt, wo.deliveredAt, wo.cancelledAt,
            partDtos, serviceDtos
        );
    }
}
