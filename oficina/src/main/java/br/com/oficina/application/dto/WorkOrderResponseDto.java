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
        List<WorkOrderPartSummaryDto> partDtos = wo.getParts().stream()
            .map(p -> new WorkOrderPartSummaryDto(
                p.getId(), p.getPartName(), p.getQuantity(), p.getUnitPrice(), p.getSubtotal()
            ))
            .toList();

        List<WorkOrderServiceSummaryDto> serviceDtos = wo.getServices().stream()
            .map(s -> new WorkOrderServiceSummaryDto(
                s.getId(), s.getServiceName(), s.getPrice(), s.getNotes()
            ))
            .toList();

        return new WorkOrderResponseDto(
            wo.getId(), wo.getOrderNumber(), wo.getStatus(),
            wo.getCustomer().name(), wo.getCustomer().cpfCnpj(),
            wo.getVehicle().licensePlate(), wo.getVehicle().brand(), wo.getVehicle().model(),
            wo.getVehicle().productionYear(),
            wo.getNotes(), wo.getTotalCost(),
            wo.getCreatedAt(), wo.getDiagnosisStartedAt(), wo.getSentForApprovalAt(),
            wo.getApprovedAt(), wo.getExecutionStartedAt(), wo.getFinishedAt(),
            wo.getDeliveredAt(), wo.getCancelledAt(),
            partDtos, serviceDtos
        );
    }
}
