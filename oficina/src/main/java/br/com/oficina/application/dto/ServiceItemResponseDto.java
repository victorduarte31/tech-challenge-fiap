package br.com.oficina.application.dto;

import br.com.oficina.domain.model.ServiceItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ServiceItemResponseDto(
    Long id,
    String name,
    String description,
    BigDecimal basePrice,
    Integer estimatedDurationMinutes,
    Boolean active,
    LocalDateTime createdAt
) {
    public static ServiceItemResponseDto from(ServiceItem s) {
        return new ServiceItemResponseDto(
            s.getId(), s.getName(), s.getDescription(), s.getBasePrice(),
            s.getEstimatedDurationMinutes(), s.getActive(), s.getCreatedAt()
        );
    }
}
