package br.com.oficina.application.dto;

import jakarta.validation.constraints.Size;

/** Decisão do dono sobre uma solicitação (nota opcional, usada na rejeição). */
public record RequestDecisionDto(
    @Size(max = 255, message = "Nota deve ter no máximo 255 caracteres") String note
) {}
