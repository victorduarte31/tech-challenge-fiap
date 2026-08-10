package br.com.oficina.application.ports.out;

import br.com.oficina.domain.event.WorkOrderStatusChangedEvent;

/**
 * Porta de saída (driven port) para notificar o cliente sobre mudanças de status
 * da OS. Declarada na camada de aplicação e implementada por um adapter na infraestrutura
 * (e-mail, SMS, etc.). A falha na notificação não deve quebrar a transição de
 * status — o adapter é responsável por tratar erros sem propagá-los.
 */
public interface NotificationGatewayPort {

    void notifyStatusChange(WorkOrderStatusChangedEvent event);
}
