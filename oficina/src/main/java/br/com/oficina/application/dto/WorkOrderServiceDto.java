package br.com.oficina.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkOrderServiceDto(
    @NotNull(message = "ID do serviço é obrigatório") Long serviceItemId,
    @Size(max = 255, message = "Observações devem ter no máximo 255 caracteres") String notes
) {}
