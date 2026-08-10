package br.com.oficina.application.dto;

import br.com.oficina.application.validation.ValidCpfCnpj;
import jakarta.validation.constraints.NotBlank;

/**
 * Body usado pelos endpoints públicos de aprovação/recusa de OS.
 *
 * <p>Duas provas independentes, sem JWT: o CPF/CNPJ confirma de quem é a OS e o
 * código de autorização — gerado no envio do orçamento e entregue apenas no
 * e-mail do cliente — prova que quem responde recebeu aquele e-mail. Sozinho, o
 * CPF/CNPJ não bastaria: o número da OS é sequencial e o documento é um dado
 * amplamente conhecido.</p>
 */
public record PublicApprovalRequestDto(
    @NotBlank(message = "CPF/CNPJ do cliente é obrigatório")
    @ValidCpfCnpj String clientCpfCnpj,

    @NotBlank(message = "Código de autorização é obrigatório (enviado no e-mail do orçamento)")
    String approvalToken
) {}
