package br.com.oficina.domain.model;

import java.math.BigDecimal;

/**
 * Linha de peça da OS. Imutável após criada. Referencia o aggregate
 * {@code Part} por identidade ({@code partId}) e congela {@code unitPrice} no
 * momento da inclusão; {@code partName} é snapshot de exibição.
 */
public final class WorkOrderPart {

    private final Long id;
    private final Long partId;
    private final String partName;
    private final Integer quantity;
    private final BigDecimal unitPrice;

    public WorkOrderPart(Long partId, String partName, Integer quantity, BigDecimal unitPrice) {
        this(null, partId, partName, quantity, unitPrice);
    }

    private WorkOrderPart(Long id, Long partId, String partName, Integer quantity, BigDecimal unitPrice) {
        this.id = id;
        this.partId = partId;
        this.partName = partName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    /** Reconstrói uma linha já persistida (uso exclusivo do mapper de persistência). */
    public static WorkOrderPart rehydrate(Long id, Long partId, String partName, Integer quantity, BigDecimal unitPrice) {
        return new WorkOrderPart(id, partId, partName, quantity, unitPrice);
    }

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getId() { return id; }
    public Long getPartId() { return partId; }
    public String getPartName() { return partName; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
