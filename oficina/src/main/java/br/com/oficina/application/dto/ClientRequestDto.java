package br.com.oficina.application.dto;

import br.com.oficina.domain.model.ClientType;
import br.com.oficina.infrastructure.validation.ValidCpfCnpj;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClientRequestDto(
    @NotBlank(message = "Nome é obrigatório") String name,
    @NotBlank(message = "CPF/CNPJ é obrigatório")
    @ValidCpfCnpj String cpfCnpj,
    @NotNull(message = "Tipo de cliente é obrigatório") ClientType clientType,
    @Email(message = "E-mail inválido") String email,
    String phone
) {}
