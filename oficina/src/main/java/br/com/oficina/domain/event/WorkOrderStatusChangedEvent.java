package br.com.oficina.domain.event;

import br.com.oficina.domain.model.VehicleSnapshot;
import br.com.oficina.domain.model.WorkOrder;
import br.com.oficina.domain.model.WorkOrderStatus;
import java.math.BigDecimal;

/**
 * Evento de domínio emitido quando a OS muda para um status que exige comunicação
 * com o cliente (aguardando aprovação, finalizada, entregue). Carrega apenas o
 * necessário para a notificação, desacoplando o disparo da porta de saída
 * ({@code NotificationGatewayPort}) do aggregate.
 */
public record WorkOrderStatusChangedEvent(
    String orderNumber,
    WorkOrderStatus newStatus,
    String customerName,
    String customerEmail,
    String vehicleDescription,
    BigDecimal totalCost
) {

    public static WorkOrderStatusChangedEvent of(WorkOrder workOrder) {
        VehicleSnapshot v = workOrder.getVehicle();
        String vehicle = "%s %s (%s)".formatted(v.brand(), v.model(), v.licensePlate());
        return new WorkOrderStatusChangedEvent(
            workOrder.getOrderNumber(),
            workOrder.getStatus(),
            workOrder.getCustomer().name(),
            workOrder.getCustomer().email(),
            vehicle,
            workOrder.getTotalCost()
        );
    }
}
