package br.com.oficina.infrastructure.persistence;

import br.com.oficina.domain.model.CustomerSnapshot;
import br.com.oficina.domain.model.VehicleSnapshot;
import br.com.oficina.domain.model.WorkOrder;
import br.com.oficina.domain.model.WorkOrderPart;
import br.com.oficina.domain.model.WorkOrderServiceItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Traduz entre o aggregate de domínio {@code WorkOrder} (puro) e a entidade de
 * persistência {@code WorkOrderEntity}. FKs de aggregates referenciados são
 * resolvidas por proxy ({@code em.getReference}) — apenas o id é gravado, sem
 * carregar a entidade.
 */
@ApplicationScoped
public class WorkOrderMapper {

    @Inject
    EntityManager em;

    public WorkOrder toDomain(WorkOrderEntity e) {
        CustomerSnapshot customer = new CustomerSnapshot(
            e.getClient().getId(), e.getClient().getName(), e.getClient().getCpfCnpj(),
            e.getClient().getEmail());

        VehicleSnapshot vehicle = new VehicleSnapshot(
            e.getVehicle().getId(), e.getVehicle().getLicensePlate(), e.getVehicle().getBrand(),
            e.getVehicle().getModel(), e.getVehicle().getProductionYear());

        List<WorkOrderPart> parts = e.getParts().stream()
            .map(p -> WorkOrderPart.rehydrate(
                p.getId(), p.getPart().getId(), p.getPart().getName(), p.getQuantity(), p.getUnitPrice()))
            .toList();

        List<WorkOrderServiceItem> services = e.getServices().stream()
            .map(s -> WorkOrderServiceItem.rehydrate(
                s.getId(), s.getServiceItem().getId(), s.getServiceItem().getName(), s.getPrice(), s.getNotes()))
            .toList();

        return WorkOrder.rehydrate(
            e.getId(), e.getOrderNumber(), customer, vehicle, e.getStatus(), e.getNotes(),
            e.getTotalCost(), e.getCreatedAt(), e.getDiagnosisStartedAt(), e.getSentForApprovalAt(),
            e.getApprovedAt(), e.getExecutionStartedAt(), e.getFinishedAt(), e.getDeliveredAt(),
            e.getCancelledAt(), parts, services);
    }

    public WorkOrderEntity toNewEntity(WorkOrder d) {
        WorkOrderEntity e = new WorkOrderEntity();
        e.setClient(em.getReference(ClientEntity.class, d.getCustomer().clientId()));
        e.setVehicle(em.getReference(VehicleEntity.class, d.getVehicle().vehicleId()));
        applyState(e, d);
        return e;
    }

    /** Sincroniza o estado do aggregate na entidade gerenciada (campos escalares + linhas). */
    public void applyState(WorkOrderEntity e, WorkOrder d) {
        e.setOrderNumber(d.getOrderNumber());
        e.setStatus(d.getStatus());
        e.setNotes(d.getNotes());
        e.setTotalCost(d.getTotalCost());
        e.setDiagnosisStartedAt(d.getDiagnosisStartedAt());
        e.setSentForApprovalAt(d.getSentForApprovalAt());
        e.setApprovedAt(d.getApprovedAt());
        e.setExecutionStartedAt(d.getExecutionStartedAt());
        e.setFinishedAt(d.getFinishedAt());
        e.setDeliveredAt(d.getDeliveredAt());
        e.setCancelledAt(d.getCancelledAt());
        reconcileParts(e, d);
        reconcileServices(e, d);
    }

    private void reconcileParts(WorkOrderEntity e, WorkOrder d) {
        Set<Long> keptIds = d.getParts().stream()
            .map(WorkOrderPart::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        e.getParts().removeIf(pe -> pe.getId() != null && !keptIds.contains(pe.getId()));

        for (WorkOrderPart line : d.getParts()) {
            if (line.getId() == null) {
                e.getParts().add(new WorkOrderPartEntity(
                    e, em.getReference(PartEntity.class, line.getPartId()), line.getQuantity(), line.getUnitPrice()));
            }
        }
    }

    private void reconcileServices(WorkOrderEntity e, WorkOrder d) {
        Set<Long> keptIds = d.getServices().stream()
            .map(WorkOrderServiceItem::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        e.getServices().removeIf(se -> se.getId() != null && !keptIds.contains(se.getId()));

        for (WorkOrderServiceItem line : d.getServices()) {
            if (line.getId() == null) {
                e.getServices().add(new WorkOrderServiceItemEntity(
                    e, em.getReference(ServiceItemEntity.class, line.getServiceItemId()), line.getPrice(), line.getNotes()));
            }
        }
    }
}
