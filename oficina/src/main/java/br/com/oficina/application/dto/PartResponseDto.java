package br.com.oficina.application.dto;

import br.com.oficina.domain.model.Part;
import br.com.oficina.domain.model.PartType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PartResponseDto(
    Long id,
    String name,
    String description,
    BigDecimal unitPrice,
    Integer stockQuantity,
    String unit,
    Integer minimumStock,
    PartType partType,
    boolean lowStock,
    boolean active,
    LocalDateTime createdAt
) {
    public static PartResponseDto from(Part p) {
        return new PartResponseDto(
            p.getId(), p.getName(), p.getDescription(), p.getUnitPrice(),
            p.getStockQuantity(), p.getUnit(), p.getMinimumStock(), p.getPartType(),
            p.isLowStock(), p.isActive(), p.getCreatedAt()
        );
    }
}
