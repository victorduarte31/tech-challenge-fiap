package br.com.oficina.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "work_order_services")
public class WorkOrderServiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_item_id", nullable = false)
    private ServiceItem serviceItem;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column
    private String notes;

    protected WorkOrderServiceItem() {
        // Required by JPA
    }

    public WorkOrderServiceItem(WorkOrder workOrder, ServiceItem serviceItem, BigDecimal price, String notes) {
        this.workOrder = workOrder;
        this.serviceItem = serviceItem;
        this.price = price;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public WorkOrder getWorkOrder() { return workOrder; }
    public ServiceItem getServiceItem() { return serviceItem; }
    public BigDecimal getPrice() { return price; }
    public String getNotes() { return notes; }
}
