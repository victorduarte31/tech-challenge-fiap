package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.BusinessException;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Solicitação de operação sensível (ajuste de estoque ou cancelamento de OS)
 * criada pela atendente e sujeita à aprovação do dono (maker-checker).
 * Mantém rastreabilidade: quem pediu, quando, motivo, quem decidiu.
 */
@Entity
@Table(name = "operation_requests")
public class OperationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "target_description", length = 150)
    private String targetDescription;

    private Integer adjustment;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", length = 255)
    private String decisionNote;

    protected OperationRequest() {
        // Required by JPA
    }

    public OperationRequest(RequestType type, Long targetId, String targetDescription,
                            Integer adjustment, String reason, String requestedBy) {
        this.type = type;
        this.targetId = targetId;
        this.targetDescription = targetDescription;
        this.adjustment = adjustment;
        this.reason = reason;
        this.requestedBy = requestedBy;
    }

    @PrePersist
    void prePersist() {
        requestedAt = LocalDateTime.now();
    }

    public void approve(String decidedBy) {
        ensurePending();
        this.status = RequestStatus.APPROVED;
        this.decidedBy = decidedBy;
        this.decidedAt = LocalDateTime.now();
    }

    public void reject(String decidedBy, String note) {
        ensurePending();
        this.status = RequestStatus.REJECTED;
        this.decidedBy = decidedBy;
        this.decidedAt = LocalDateTime.now();
        this.decisionNote = note;
    }

    private void ensurePending() {
        if (status != RequestStatus.PENDING) {
            throw new BusinessException("Solicitação já foi " +
                (status == RequestStatus.APPROVED ? "aprovada" : "rejeitada") + ".");
        }
    }

    public Long getId() { return id; }
    public RequestType getType() { return type; }
    public RequestStatus getStatus() { return status; }
    public Long getTargetId() { return targetId; }
    public String getTargetDescription() { return targetDescription; }
    public Integer getAdjustment() { return adjustment; }
    public String getReason() { return reason; }
    public String getRequestedBy() { return requestedBy; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public String getDecidedBy() { return decidedBy; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public String getDecisionNote() { return decisionNote; }
}
