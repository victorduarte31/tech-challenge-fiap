package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.InvalidStatusTransitionException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "work_orders")
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "order_number", unique = true, length = 20)
    public String orderNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id", nullable = false)
    public Client client;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehicle_id", nullable = false)
    public Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    public WorkOrderStatus status = WorkOrderStatus.RECEIVED;

    @Column(length = 500)
    public String notes;

    @Column(name = "total_cost", precision = 10, scale = 2)
    public BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @Column(name = "diagnosis_started_at")
    public LocalDateTime diagnosisStartedAt;

    @Column(name = "sent_for_approval_at")
    public LocalDateTime sentForApprovalAt;

    @Column(name = "approved_at")
    public LocalDateTime approvedAt;

    @Column(name = "execution_started_at")
    public LocalDateTime executionStartedAt;

    @Column(name = "finished_at")
    public LocalDateTime finishedAt;

    @Column(name = "delivered_at")
    public LocalDateTime deliveredAt;

    @Column(name = "cancelled_at")
    public LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    public List<WorkOrderPart> parts = new ArrayList<>();

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    public List<WorkOrderServiceItem> services = new ArrayList<>();

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ===== Domain State Machine =====

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
            .map(s -> s.price)
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
}
