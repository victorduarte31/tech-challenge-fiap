package br.com.oficina.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WorkOrderServiceDto(
    @NotNull(message = "ID do serviço é obrigatório") Long serviceItemId,
    String notes
) {}
