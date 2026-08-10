package br.com.oficina.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderStatusTest {

    @Test
    void activeByPriority_shouldFollowTheOrderRequiredByTheBusiness() {
        assertThat(WorkOrderStatus.activeByPriority()).containsExactly(
            WorkOrderStatus.IN_EXECUTION,
            WorkOrderStatus.AWAITING_APPROVAL,
            WorkOrderStatus.IN_DIAGNOSIS,
            WorkOrderStatus.RECEIVED);
    }

    @Test
    void terminalStatuses_shouldBeExcludedFromTheActiveQueue() {
        assertThat(WorkOrderStatus.terminalStatuses()).containsExactlyInAnyOrder(
            WorkOrderStatus.FINISHED,
            WorkOrderStatus.DELIVERED,
            WorkOrderStatus.CANCELLED);
    }

    @Test
    void everyStatusIsEitherActiveOrTerminal() {
        assertThat(WorkOrderStatus.activeByPriority().size() + WorkOrderStatus.terminalStatuses().size())
            .isEqualTo(WorkOrderStatus.values().length);
    }

    @Test
    void isTerminal_shouldMatchTheDeclaredClassification() {
        assertThat(WorkOrderStatus.RECEIVED.isTerminal()).isFalse();
        assertThat(WorkOrderStatus.IN_EXECUTION.isTerminal()).isFalse();
        assertThat(WorkOrderStatus.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    void listingPriority_shouldPutExecutionFirstAndReceivedLast() {
        assertThat(WorkOrderStatus.IN_EXECUTION.listingPriority())
            .isLessThan(WorkOrderStatus.AWAITING_APPROVAL.listingPriority());
        assertThat(WorkOrderStatus.AWAITING_APPROVAL.listingPriority())
            .isLessThan(WorkOrderStatus.IN_DIAGNOSIS.listingPriority());
        assertThat(WorkOrderStatus.IN_DIAGNOSIS.listingPriority())
            .isLessThan(WorkOrderStatus.RECEIVED.listingPriority());
    }

    /**
     * O nome das constantes é persistido ({@code @Enumerated(EnumType.STRING)}) e
     * interpolado na consulta de ordenação — renomear quebra dados existentes.
     */
    @Test
    void constantNames_areAPersistenceContract() {
        assertThat(WorkOrderStatus.values()).extracting(Enum::name).containsExactlyInAnyOrder(
            "RECEIVED", "IN_DIAGNOSIS", "AWAITING_APPROVAL", "IN_EXECUTION",
            "FINISHED", "DELIVERED", "CANCELLED");
    }
}
