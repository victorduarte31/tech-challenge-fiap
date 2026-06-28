package br.com.oficina.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Serviço oferecido pela oficina — modelo de domínio puro, sem dependência de
 * framework de persistência. A tradução para a tabela {@code service_items} é feita
 * pelo {@code ServiceItemEntity}/{@code ServiceItemMapper} na camada de infraestrutura.
 */
public class ServiceItem {

    private Long id;
    private String name;
    private String description;
    private BigDecimal basePrice;
    private Integer estimatedDurationMinutes;
    private Boolean active = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ServiceItem(String name, String description, BigDecimal basePrice, Integer estimatedDurationMinutes) {
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.active = true;
    }

    private ServiceItem() {
        // Reconstrução via rehydrate().
    }

    /** Reconstrói o serviço a partir da persistência (uso exclusivo do mapper). */
    public static ServiceItem rehydrate(Long id, String name, String description, BigDecimal basePrice,
                                        Integer estimatedDurationMinutes, Boolean active,
                                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        ServiceItem s = new ServiceItem();
        s.id = id;
        s.name = name;
        s.description = description;
        s.basePrice = basePrice;
        s.estimatedDurationMinutes = estimatedDurationMinutes;
        s.active = active;
        s.createdAt = createdAt;
        s.updatedAt = updatedAt;
        return s;
    }

    public void update(String name, String description, BigDecimal basePrice, Integer estimatedDurationMinutes) {
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
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

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getBasePrice() { return basePrice; }
    public Integer getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
    public Boolean getActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
