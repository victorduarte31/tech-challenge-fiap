package br.com.oficina.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WorkOrderPartDto(
    @NotNull(message = "ID da peça é obrigatório") Long partId,
    @NotNull(message = "Quantidade é obrigatória")
    @Min(value = 1, message = "Quantidade mínima é 1") Integer quantity
) {}
