package br.com.oficina.application.dto;

import br.com.oficina.infrastructure.validation.ValidCpfCnpj;
import jakarta.validation.constraints.NotBlank;

/**
 * Body usado pelos endpoints públicos de aprovação/rejeição de OS.
 * Exige CPF/CNPJ como prova mínima de identidade do cliente — sem JWT.
 */
public record PublicApprovalRequestDto(
    @NotBlank(message = "CPF/CNPJ do cliente é obrigatório")
    @ValidCpfCnpj String clientCpfCnpj
) {}
