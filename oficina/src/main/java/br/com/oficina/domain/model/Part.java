package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Peça ou insumo com controle de estoque — modelo de domínio puro, sem dependência
 * de framework de persistência. O controle de concorrência otimista ({@code version})
 * é refletido na persistência pelo {@code PartEntity} (campo {@code @Version}).
 */
public class Part {

    private Long id;
    private String name;
    private String description;
    private BigDecimal unitPrice;
    private Integer stockQuantity;
    private String unit;
    private Integer minimumStock = 0;
    private PartType partType = PartType.PECA;
    private Boolean active = true;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    private Part() {
        // Reconstrução via rehydrate().
    }

    /** Reconstrói a peça a partir da persistência (uso exclusivo do mapper). */
    public static Part rehydrate(Long id, String name, String description, BigDecimal unitPrice,
                                 Integer stockQuantity, String unit, Integer minimumStock, PartType partType,
                                 Boolean active, Long version, LocalDateTime createdAt, LocalDateTime updatedAt) {
        Part p = new Part();
        p.id = id;
        p.name = name;
        p.description = description;
        p.unitPrice = unitPrice;
        p.stockQuantity = stockQuantity;
        p.unit = unit;
        p.minimumStock = minimumStock != null ? minimumStock : 0;
        p.partType = partType != null ? partType : PartType.PECA;
        p.active = active;
        p.version = version;
        p.createdAt = createdAt;
        p.updatedAt = updatedAt;
        return p;
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

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
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
    public Boolean getActive() { return active; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
