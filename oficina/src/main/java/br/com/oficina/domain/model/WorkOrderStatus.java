package br.com.oficina.domain.model;

public enum WorkOrderStatus {
    RECEIVED,
    IN_DIAGNOSIS,
    AWAITING_APPROVAL,
    IN_EXECUTION,
    FINISHED,
    DELIVERED,
    CANCELLED
}
