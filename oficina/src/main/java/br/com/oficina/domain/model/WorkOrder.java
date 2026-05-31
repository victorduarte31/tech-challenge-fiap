package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.InvalidStatusTransitionException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "work_orders")
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", unique = true, length = 20)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private WorkOrderStatus status = WorkOrderStatus.RECEIVED;

    @Column(length = 500)
    private String notes;

    @Column(name = "total_cost", precision = 10, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "diagnosis_started_at")
    private LocalDateTime diagnosisStartedAt;

    @Column(name = "sent_for_approval_at")
    private LocalDateTime sentForApprovalAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "execution_started_at")
    private LocalDateTime executionStartedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private final List<WorkOrderPart> parts = new ArrayList<>();

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private final List<WorkOrderServiceItem> services = new ArrayList<>();

    protected WorkOrder() {
        // Required by JPA
    }

    public WorkOrder(Client client, Vehicle vehicle, String notes) {
        this.client = client;
        this.vehicle = vehicle;
        this.notes = notes;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
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

    // ===== Manipulação de itens (delega regras ao agregado) =====

    public WorkOrderPart addPart(Part part, int quantity) {
        ensureEditable();
        part.decreaseStock(quantity);
        WorkOrderPart line = new WorkOrderPart(this, part, quantity, part.getUnitPrice());
        this.parts.add(line);
        recalculateTotalCost();
        return line;
    }

    public void removePart(Long partLineId) {
        ensureEditable();
        WorkOrderPart line = this.parts.stream()
            .filter(p -> p.getId().equals(partLineId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Linha de peça", partLineId));
        line.getPart().increaseStock(line.getQuantity());
        this.parts.remove(line);
        recalculateTotalCost();
    }

    public WorkOrderServiceItem addService(ServiceItem serviceItem, String serviceNotes) {
        ensureEditable();
        if (!serviceItem.isActive()) {
            throw new BusinessException("Serviço inativo: " + serviceItem.getName());
        }
        WorkOrderServiceItem line = new WorkOrderServiceItem(this, serviceItem, serviceItem.getBasePrice(), serviceNotes);
        this.services.add(line);
        recalculateTotalCost();
        return line;
    }

    public void removeService(Long serviceLineId) {
        ensureEditable();
        boolean removed = this.services.removeIf(s -> s.getId().equals(serviceLineId));
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
        restoreStockOfAllParts();
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
        restoreStockOfAllParts();
        status = WorkOrderStatus.CANCELLED;
        cancelledAt = LocalDateTime.now();
    }

    private void restoreStockOfAllParts() {
        for (WorkOrderPart line : parts) {
            line.getPart().increaseStock(line.getQuantity());
        }
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
        return java.time.Duration.between(executionStartedAt, finishedAt).toMinutes();
    }

    // ===== Getters =====

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public Client getClient() { return client; }
    public Vehicle getVehicle() { return vehicle; }
    public WorkOrderStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public BigDecimal getTotalCost() { return totalCost; }
    /** Orçamento da OS, gerado automaticamente a partir de peças e serviços incluídos. */
    public BigDecimal getBudget() { return totalCost; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
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
