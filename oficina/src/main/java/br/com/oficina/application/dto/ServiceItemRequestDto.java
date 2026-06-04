package br.com.oficina.application.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ServiceItemRequestDto(
    @NotBlank(message = "Nome do serviço é obrigatório")
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres") String name,
    @Size(max = 255, message = "Descrição deve ter no máximo 255 caracteres") String description,
    @NotNull(message = "Preço base é obrigatório")
    @DecimalMin(value = "0.01", message = "Preço deve ser maior que zero")
    @Digits(integer = 8, fraction = 2, message = "Preço excede o limite permitido (máx. 8 dígitos inteiros e 2 decimais)") BigDecimal basePrice,
    @NotNull(message = "Duração estimada é obrigatória")
    @Min(value = 1, message = "Duração mínima é 1 minuto") Integer estimatedDurationMinutes
) {}
