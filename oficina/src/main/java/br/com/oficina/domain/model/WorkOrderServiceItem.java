package br.com.oficina.domain.model;

import java.math.BigDecimal;

/**
 * Linha de serviço da OS. Imutável após criada. Referencia o aggregate
 * {@code ServiceItem} por identidade ({@code serviceItemId}) e congela
 * {@code price} no momento da inclusão; {@code serviceName} é snapshot de exibição.
 */
public final class WorkOrderServiceItem {

    private final Long id;
    private final Long serviceItemId;
    private final String serviceName;
    private final BigDecimal price;
    private final String notes;

    public WorkOrderServiceItem(Long serviceItemId, String serviceName, BigDecimal price, String notes) {
        this(null, serviceItemId, serviceName, price, notes);
    }

    private WorkOrderServiceItem(Long id, Long serviceItemId, String serviceName, BigDecimal price, String notes) {
        this.id = id;
        this.serviceItemId = serviceItemId;
        this.serviceName = serviceName;
        this.price = price;
        this.notes = notes;
    }

    /** Reconstrói uma linha já persistida (uso exclusivo do mapper de persistência). */
    public static WorkOrderServiceItem rehydrate(Long id, Long serviceItemId, String serviceName, BigDecimal price, String notes) {
        return new WorkOrderServiceItem(id, serviceItemId, serviceName, price, notes);
    }

    public Long getId() { return id; }
    public Long getServiceItemId() { return serviceItemId; }
    public String getServiceName() { return serviceName; }
    public BigDecimal getPrice() { return price; }
    public String getNotes() { return notes; }
}
