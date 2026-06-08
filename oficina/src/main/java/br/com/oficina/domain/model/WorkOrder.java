package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.InvalidStatusTransitionException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate raiz da Ordem de Serviço — modelo de domínio puro, sem dependência
 * de framework de persistência. Referencia os demais aggregates
 * ({@code Client}, {@code Vehicle}, {@code Part}, {@code ServiceItem}) por
 * identidade, via snapshots. A reserva/devolução de estoque é coordenada pela
 * camada de aplicação (cross-aggregate), não pelo aggregate.
 */
public class WorkOrder {

    private Long id;
    private String orderNumber;
    private CustomerSnapshot customer;
    private VehicleSnapshot vehicle;
    private WorkOrderStatus status;
    private String notes;
    private BigDecimal totalCost;
    private LocalDateTime createdAt;
    private LocalDateTime diagnosisStartedAt;
    private LocalDateTime sentForApprovalAt;
    private LocalDateTime approvedAt;
    private LocalDateTime executionStartedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private final List<WorkOrderPart> parts = new ArrayList<>();
    private final List<WorkOrderServiceItem> services = new ArrayList<>();

    public WorkOrder(CustomerSnapshot customer, VehicleSnapshot vehicle, String notes) {
        this.customer = customer;
        this.vehicle = vehicle;
        this.notes = notes;
        this.status = WorkOrderStatus.RECEIVED;
        this.totalCost = BigDecimal.ZERO;
        this.createdAt = LocalDateTime.now();
    }

    private WorkOrder() {
        // Reconstrução via rehydrate().
    }

    /** Reconstrói o aggregate a partir da persistência (uso exclusivo do mapper). */
    public static WorkOrder rehydrate(
            Long id, String orderNumber, CustomerSnapshot customer, VehicleSnapshot vehicle,
            WorkOrderStatus status, String notes, BigDecimal totalCost, LocalDateTime createdAt,
            LocalDateTime diagnosisStartedAt, LocalDateTime sentForApprovalAt, LocalDateTime approvedAt,
            LocalDateTime executionStartedAt, LocalDateTime finishedAt, LocalDateTime deliveredAt,
            LocalDateTime cancelledAt, List<WorkOrderPart> parts, List<WorkOrderServiceItem> services) {
        WorkOrder wo = new WorkOrder();
        wo.id = id;
        wo.orderNumber = orderNumber;
        wo.customer = customer;
        wo.vehicle = vehicle;
        wo.status = status;
        wo.notes = notes;
        wo.totalCost = totalCost;
        wo.createdAt = createdAt;
        wo.diagnosisStartedAt = diagnosisStartedAt;
        wo.sentForApprovalAt = sentForApprovalAt;
        wo.approvedAt = approvedAt;
        wo.executionStartedAt = executionStartedAt;
        wo.finishedAt = finishedAt;
        wo.deliveredAt = deliveredAt;
        wo.cancelledAt = cancelledAt;
        if (parts != null) wo.parts.addAll(parts);
        if (services != null) wo.services.addAll(services);
        return wo;
    }

    // ===== Identidade =====

    public void assignOrderNumber(String orderNumber) {
        if (this.orderNumber != null) {
            throw new BusinessException("Número da OS já foi atribuído: " + this.orderNumber);
        }
        this.orderNumber = orderNumber;
    }

    public void updateNotes(String notes) {
        this.notes = notes;
    }

    // ===== Manipulação de itens =====
    // O domínio valida estado e recalcula o orçamento. A validação de
    // peça/serviço ativo e o controle de estoque ocorrem na camada de aplicação,
    // que coordena os aggregates Part/ServiceItem.

    public WorkOrderPart addPart(Long partId, String partName, int quantity, BigDecimal unitPrice) {
        ensureEditable();
        WorkOrderPart line = new WorkOrderPart(partId, partName, quantity, unitPrice);
        this.parts.add(line);
        recalculateTotalCost();
        return line;
    }

    /** Remove a linha e a devolve, para que a aplicação restaure o estoque correspondente. */
    public WorkOrderPart removePart(Long partLineId) {
        ensureEditable();
        WorkOrderPart line = this.parts.stream()
            .filter(p -> p.getId() != null && p.getId().equals(partLineId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Linha de peça", partLineId));
        this.parts.remove(line);
        recalculateTotalCost();
        return line;
    }

    public WorkOrderServiceItem addService(Long serviceItemId, String serviceName, BigDecimal price, String serviceNotes) {
        ensureEditable();
        WorkOrderServiceItem line = new WorkOrderServiceItem(serviceItemId, serviceName, price, serviceNotes);
        this.services.add(line);
        recalculateTotalCost();
        return line;
    }

    public void removeService(Long serviceLineId) {
        ensureEditable();
        boolean removed = this.services.removeIf(s -> s.getId() != null && s.getId().equals(serviceLineId));
        if (!removed) {
            throw new ResourceNotFoundException("Linha de serviço", serviceLineId);
        }
        recalculateTotalCost();
    }

    private void ensureEditable() {
        if (status != WorkOrderStatus.RECEIVED && status != WorkOrderStatus.IN_DIAGNOSIS) {
            throw new BusinessException("Não é possível editar uma OS com status: " + status);
        }
    }

    // ===== State Machine =====

    public void startDiagnosis() {
        requireStatus(WorkOrderStatus.RECEIVED);
        status = WorkOrderStatus.IN_DIAGNOSIS;
        diagnosisStartedAt = LocalDateTime.now();
    }

    public void sendForApproval() {
        requireStatus(WorkOrderStatus.IN_DIAGNOSIS);
        recalculateTotalCost();
        status = WorkOrderStatus.AWAITING_APPROVAL;
        sentForApprovalAt = LocalDateTime.now();
    }

    public void approve() {
        requireStatus(WorkOrderStatus.AWAITING_APPROVAL);
        status = WorkOrderStatus.IN_EXECUTION;
        approvedAt = LocalDateTime.now();
        executionStartedAt = LocalDateTime.now();
    }

    public void reject() {
        requireStatus(WorkOrderStatus.AWAITING_APPROVAL);
        status = WorkOrderStatus.CANCELLED;
        cancelledAt = LocalDateTime.now();
    }

    public void complete() {
        requireStatus(WorkOrderStatus.IN_EXECUTION);
        status = WorkOrderStatus.FINISHED;
        finishedAt = LocalDateTime.now();
    }

    public void deliver() {
        requireStatus(WorkOrderStatus.FINISHED);
        status = WorkOrderStatus.DELIVERED;
        deliveredAt = LocalDateTime.now();
    }

    public void cancel() {
        if (status == WorkOrderStatus.DELIVERED || status == WorkOrderStatus.CANCELLED) {
            throw new InvalidStatusTransitionException(
                "Não é possível cancelar uma OS com status: " + status
            );
        }
        status = WorkOrderStatus.CANCELLED;
        cancelledAt = LocalDateTime.now();
    }

    public void recalculateTotalCost() {
        BigDecimal partsCost = parts.stream()
            .map(WorkOrderPart::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal servicesCost = services.stream()
            .map(WorkOrderServiceItem::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        totalCost = partsCost.add(servicesCost);
    }

    private void requireStatus(WorkOrderStatus required) {
        if (status != required) {
            throw new InvalidStatusTransitionException(
                "Operação inválida. Status atual: " + status + ". Status esperado: " + required
            );
        }
    }

    public long getExecutionDurationMinutes() {
        if (executionStartedAt == null || finishedAt == null) {
            return 0;
        }
        return Duration.between(executionStartedAt, finishedAt).toMinutes();
    }

    // ===== Getters =====

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public CustomerSnapshot getCustomer() { return customer; }
    public VehicleSnapshot getVehicle() { return vehicle; }
    public WorkOrderStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public BigDecimal getTotalCost() { return totalCost; }
    /** Orçamento da OS, gerado automaticamente a partir de peças e serviços incluídos. */
    public BigDecimal getBudget() { return totalCost; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getDiagnosisStartedAt() { return diagnosisStartedAt; }
    public LocalDateTime getSentForApprovalAt() { return sentForApprovalAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public LocalDateTime getExecutionStartedAt() { return executionStartedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public List<WorkOrderPart> getParts() { return Collections.unmodifiableList(parts); }
    public List<WorkOrderServiceItem> getServices() { return Collections.unmodifiableList(services); }
}
