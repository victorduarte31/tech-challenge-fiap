package br.com.oficina.infrastructure.persistence;

import br.com.oficina.domain.model.ServiceItem;
import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Entidade de persistência da linha de serviço da OS. Mapeia {@code work_order_services}.
 */
@Entity
@Table(name = "work_order_services")
public class WorkOrderServiceItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrderEntity workOrder;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_item_id", nullable = false)
    private ServiceItem serviceItem;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column
    private String notes;

    public WorkOrderServiceItemEntity() {
    }

    public WorkOrderServiceItemEntity(WorkOrderEntity workOrder, ServiceItem serviceItem, BigDecimal price, String notes) {
        this.workOrder = workOrder;
        this.serviceItem = serviceItem;
        this.price = price;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public WorkOrderEntity getWorkOrder() { return workOrder; }
    public ServiceItem getServiceItem() { return serviceItem; }
    public BigDecimal getPrice() { return price; }
    public String getNotes() { return notes; }
}
