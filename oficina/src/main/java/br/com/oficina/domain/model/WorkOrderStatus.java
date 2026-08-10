package br.com.oficina.domain.model;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Estados da Ordem de Serviço.
 *
 * <p>A ordem de exibição da fila de trabalho ({@code IN_EXECUTION >
 * AWAITING_APPROVAL > IN_DIAGNOSIS > RECEIVED}) é regra de negócio e por isso
 * mora aqui, não numa string de consulta no adapter de persistência: assim ela é
 * testável sem banco e existe uma única fonte da verdade para qualquer adapter
 * que precise ordenar a fila.</p>
 *
 * <p>Estados terminais são excluídos da fila ativa (exclusão lógica, nunca
 * física). {@code CANCELLED} é terminal pelo mesmo motivo que {@code FINISHED} e
 * {@code DELIVERED}: não há mais trabalho a fazer sobre a OS.</p>
 *
 * <p>A ordem de declaração das constantes é irrelevante para a persistência
 * ({@code @Enumerated(EnumType.STRING)}), mas é mantida na sequência natural do
 * fluxo para legibilidade.</p>
 */
public enum WorkOrderStatus {

    RECEIVED(3, false),
    IN_DIAGNOSIS(2, false),
    AWAITING_APPROVAL(1, false),
    IN_EXECUTION(0, false),
    // Prioridade 99 = terminal; nunca chega a ser usada na ordenação, já que
    // esses estados são filtrados antes. Existe só para manter o campo total.
    FINISHED(99, true),
    DELIVERED(99, true),
    CANCELLED(99, true);

    private final int listingPriority;
    private final boolean terminal;

    WorkOrderStatus(int listingPriority, boolean terminal) {
        this.listingPriority = listingPriority;
        this.terminal = terminal;
    }

    /** Menor valor = aparece primeiro na fila de trabalho. */
    public int listingPriority() {
        return listingPriority;
    }

    /** OS encerrada: não aparece na listagem ativa e não admite novas transições. */
    public boolean isTerminal() {
        return terminal;
    }

    /** Estados ativos, na ordem exata em que devem aparecer na listagem. */
    public static List<WorkOrderStatus> activeByPriority() {
        return Arrays.stream(values())
            .filter(status -> !status.terminal)
            .sorted(Comparator.comparingInt(WorkOrderStatus::listingPriority))
            .toList();
    }

    /** Estados encerrados, excluídos logicamente da listagem ativa. */
    public static List<WorkOrderStatus> terminalStatuses() {
        return Arrays.stream(values()).filter(WorkOrderStatus::isTerminal).toList();
    }
}
