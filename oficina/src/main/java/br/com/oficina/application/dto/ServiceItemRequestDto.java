package br.com.oficina.application.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ServiceItemRequestDto(
    @NotBlank(message = "Nome do serviço é obrigatório") String name,
    String description,
    @NotNull(message = "Preço base é obrigatório")
    @DecimalMin(value = "0.01", message = "Preço deve ser maior que zero") BigDecimal basePrice,
    @NotNull(message = "Duração estimada é obrigatória")
    @Min(value = 1, message = "Duração mínima é 1 minuto") Integer estimatedDurationMinutes
) {}
