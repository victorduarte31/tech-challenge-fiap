package br.com.oficina.application.dto;

import br.com.oficina.domain.model.Part;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PartResponseDto(
    Long id,
    String name,
    String description,
    BigDecimal unitPrice,
    Integer stockQuantity,
    String unit,
    LocalDateTime createdAt
) {
    public static PartResponseDto from(Part p) {
        return new PartResponseDto(
            p.id, p.name, p.description, p.unitPrice, p.stockQuantity, p.unit, p.createdAt
        );
    }
}
