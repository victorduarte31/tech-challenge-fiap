package br.com.oficina.application.dto;

import br.com.oficina.domain.model.WorkOrderStatus;

/**
 * Tradução de apresentação do {@link WorkOrderStatus} para um rótulo em português,
 * exibido ao cliente no acompanhamento público da OS. Vive na camada de aplicação
 * (concern de apresentação) e não no enum de domínio — o enum permanece em
 * inglês/código como contrato interno. O {@code switch} exaustivo garante, em tempo
 * de compilação, que todo valor do enum possui exatamente um rótulo.
 */
public final class WorkOrderStatusLabel {

    private WorkOrderStatusLabel() {
    }

    public static String of(WorkOrderStatus status) {
        return switch (status) {
            case RECEIVED -> "Recebida";
            case IN_DIAGNOSIS -> "Diagnóstico";
            case AWAITING_APPROVAL -> "Aguardando Aprovação";
            case IN_EXECUTION -> "Execução";
            case FINISHED -> "Finalizada";
            case DELIVERED -> "Entregue";
            case CANCELLED -> "Cancelada";
        };
    }
}
