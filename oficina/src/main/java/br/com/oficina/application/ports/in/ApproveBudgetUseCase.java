package br.com.oficina.application.ports.in;

import br.com.oficina.application.dto.WorkOrderResponseDto;

/**
 * Porta de entrada (driver port) para aprovação/recusa remota do orçamento pelo
 * próprio cliente (canal público), identificada pelo número da OS e validada pelo
 * CPF/CNPJ. Suporta notificações externas de aprovação de orçamento.
 */
public interface ApproveBudgetUseCase {

    WorkOrderResponseDto approveByOrderNumber(String orderNumber, String clientCpfCnpj);

    WorkOrderResponseDto rejectByOrderNumber(String orderNumber, String clientCpfCnpj);
}
