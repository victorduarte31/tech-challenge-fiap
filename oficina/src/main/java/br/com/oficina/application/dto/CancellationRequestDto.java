package br.com.oficina.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Solicitação de cancelamento de OS (criada pela atendente; aprovada pelo dono). */
public record CancellationRequestDto(
    @NotNull(message = "ID da OS é obrigatório") Long workOrderId,
    @NotBlank(message = "Justificativa é obrigatória")
    @Size(max = 255, message = "Justificativa deve ter no máximo 255 caracteres") String reason
) {}
