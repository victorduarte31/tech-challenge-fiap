package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.BusinessException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "parts")
public class Part {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column
    private String description;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(nullable = false, length = 10)
    private String unit;

    @Column(name = "minimum_stock", nullable = false)
    private Integer minimumStock = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "part_type", nullable = false, length = 10)
    private PartType partType = PartType.PECA;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Part() {
        // Required by JPA
    }

    public Part(String name, String description, BigDecimal unitPrice, Integer stockQuantity, String unit) {
        this(name, description, unitPrice, stockQuantity, unit, 0, PartType.PECA);
    }

    public Part(String name, String description, BigDecimal unitPrice, Integer stockQuantity, String unit,
                Integer minimumStock, PartType partType) {
        this.name = name;
        this.description = description;
        this.unitPrice = unitPrice;
        this.stockQuantity = stockQuantity;
        this.unit = unit;
        this.minimumStock = minimumStock != null ? minimumStock : 0;
        this.partType = partType != null ? partType : PartType.PECA;
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

    public void update(String name, String description, BigDecimal unitPrice, Integer stockQuantity, String unit,
                       Integer minimumStock, PartType partType) {
        this.name = name;
        this.description = description;
        this.unitPrice = unitPrice;
        this.stockQuantity = stockQuantity;
        this.unit = unit;
        this.minimumStock = minimumStock != null ? minimumStock : 0;
        this.partType = partType != null ? partType : PartType.PECA;
    }

    /**
     * Indica necessidade de reposição: estoque atual igual ou abaixo do mínimo definido para a peça.
     */
    public boolean isLowStock() {
        return minimumStock != null && stockQuantity <= minimumStock;
    }

    public void decreaseStock(int quantity) {
        if (this.stockQuantity < quantity) {
            throw new BusinessException(
                "Estoque insuficiente para a peça '" + name + "'. Disponível: " + stockQuantity
            );
        }
        this.stockQuantity -= quantity;
    }

    public void increaseStock(int quantity) {
        this.stockQuantity += quantity;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public Integer getStockQuantity() { return stockQuantity; }
    public String getUnit() { return unit; }
    public Integer getMinimumStock() { return minimumStock; }
    public PartType getPartType() { return partType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
