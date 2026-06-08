package br.com.oficina.infrastructure.persistence;

import br.com.oficina.domain.model.Client;
import br.com.oficina.domain.model.Vehicle;
import br.com.oficina.domain.model.WorkOrderStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade de persistência da OS. Mapeia a tabela {@code work_orders} e isola o
 * JPA do aggregate de domínio {@code WorkOrder}. Mantém FK para os supporting
 * domains ({@code Client}/{@code Vehicle}), que permanecem entidades JPA.
 */
@Entity
@Table(name = "work_orders")
public class WorkOrderEntity {

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
    private final List<WorkOrderPartEntity> parts = new ArrayList<>();

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private final List<WorkOrderServiceItemEntity> services = new ArrayList<>();

    public WorkOrderEntity() {
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

    public Long getId() { return id; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }

    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }

    public WorkOrderStatus getStatus() { return status; }
    public void setStatus(WorkOrderStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getDiagnosisStartedAt() { return diagnosisStartedAt; }
    public void setDiagnosisStartedAt(LocalDateTime v) { this.diagnosisStartedAt = v; }

    public LocalDateTime getSentForApprovalAt() { return sentForApprovalAt; }
    public void setSentForApprovalAt(LocalDateTime v) { this.sentForApprovalAt = v; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime v) { this.approvedAt = v; }

    public LocalDateTime getExecutionStartedAt() { return executionStartedAt; }
    public void setExecutionStartedAt(LocalDateTime v) { this.executionStartedAt = v; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime v) { this.finishedAt = v; }

    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime v) { this.deliveredAt = v; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime v) { this.cancelledAt = v; }

    public List<WorkOrderPartEntity> getParts() { return parts; }
    public List<WorkOrderServiceItemEntity> getServices() { return services; }
}
