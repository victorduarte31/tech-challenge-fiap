package br.com.oficina.application.dto;

import br.com.oficina.domain.model.PartType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PartRequestDto(
    @NotBlank(message = "Nome da peça é obrigatório")
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres") String name,
    @Size(max = 255, message = "Descrição deve ter no máximo 255 caracteres") String description,
    @NotNull(message = "Preço unitário é obrigatório")
    @DecimalMin(value = "0.01", message = "Preço deve ser maior que zero")
    @Digits(integer = 8, fraction = 2, message = "Preço excede o limite permitido (máx. 8 dígitos inteiros e 2 decimais)") BigDecimal unitPrice,
    @NotNull(message = "Quantidade em estoque é obrigatória")
    @Min(value = 0, message = "Estoque não pode ser negativo") Integer stockQuantity,
    @NotBlank(message = "Unidade é obrigatória")
    @Size(max = 10, message = "Unidade deve ter no máximo 10 caracteres") String unit,
    @Min(value = 0, message = "Estoque mínimo não pode ser negativo") Integer minimumStock,
    PartType partType
) {}
