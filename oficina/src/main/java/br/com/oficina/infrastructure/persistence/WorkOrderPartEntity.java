package br.com.oficina.infrastructure.persistence;

import br.com.oficina.domain.model.Part;
import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Entidade de persistência da linha de peça da OS. Mapeia {@code work_order_parts}.
 */
@Entity
@Table(name = "work_order_parts")
public class WorkOrderPartEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrderEntity workOrder;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    public WorkOrderPartEntity() {
    }

    public WorkOrderPartEntity(WorkOrderEntity workOrder, Part part, Integer quantity, BigDecimal unitPrice) {
        this.workOrder = workOrder;
        this.part = part;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Long getId() { return id; }
    public WorkOrderEntity getWorkOrder() { return workOrder; }
    public Part getPart() { return part; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
