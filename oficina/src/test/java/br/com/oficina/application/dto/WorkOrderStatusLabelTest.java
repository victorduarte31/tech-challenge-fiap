package br.com.oficina.application.dto;

import br.com.oficina.domain.model.WorkOrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderStatusLabelTest {

    @ParameterizedTest
    @CsvSource({
        "RECEIVED, Recebida",
        "IN_DIAGNOSIS, Diagnóstico",
        "AWAITING_APPROVAL, Aguardando Aprovação",
        "IN_EXECUTION, Execução",
        "FINISHED, Finalizada",
        "DELIVERED, Entregue",
        "CANCELLED, Cancelada"
    })
    void mapeiaCadaStatusParaSeuLabelEmPortugues(WorkOrderStatus status, String expectedLabel) {
        assertThat(WorkOrderStatusLabel.of(status)).isEqualTo(expectedLabel);
    }

    @ParameterizedTest
    @EnumSource(WorkOrderStatus.class)
    void todoStatusPossuiLabelNaoVazio(WorkOrderStatus status) {
        assertThat(WorkOrderStatusLabel.of(status)).isNotBlank();
    }

    @Test
    void labelsSaoUnicosSemAmbiguidade() {
        long distintos = java.util.Arrays.stream(WorkOrderStatus.values())
            .map(WorkOrderStatusLabel::of)
            .distinct()
            .count();
        assertThat(distintos).isEqualTo(WorkOrderStatus.values().length);
    }
}
