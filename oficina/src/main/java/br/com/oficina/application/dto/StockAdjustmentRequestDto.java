package br.com.oficina.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Solicitação de ajuste de estoque (criada pela atendente; aprovada pelo dono). */
public record StockAdjustmentRequestDto(
    @NotNull(message = "ID da peça é obrigatório") Long partId,
    @NotNull(message = "Ajuste é obrigatório") Integer adjustment,
    @NotBlank(message = "Justificativa é obrigatória")
    @Size(max = 255, message = "Justificativa deve ter no máximo 255 caracteres") String reason
) {}
