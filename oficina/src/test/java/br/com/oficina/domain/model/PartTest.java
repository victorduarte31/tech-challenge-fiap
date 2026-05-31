package br.com.oficina.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class PartTest {

    @Test
    void newPart_withDefaults_shouldBePecaWithZeroMinimumStock() {
        Part part = new Part("Filtro", "Filtro de óleo", new BigDecimal("29.90"), 10, "UN");

        assertThat(part.getPartType()).isEqualTo(PartType.PECA);
        assertThat(part.getMinimumStock()).isZero();
    }

    @Test
    void newPart_withFullConstructor_shouldKeepMinimumStockAndType() {
        Part part = new Part("Óleo 5W30", "Óleo sintético", new BigDecimal("45.00"), 8, "L", 5, PartType.INSUMO);

        assertThat(part.getMinimumStock()).isEqualTo(5);
        assertThat(part.getPartType()).isEqualTo(PartType.INSUMO);
    }

    @Test
    void isLowStock_whenStockAtOrBelowMinimum_shouldBeTrue() {
        Part atMinimum = new Part("Pastilha", null, BigDecimal.TEN, 5, "UN", 5, PartType.PECA);
        Part belowMinimum = new Part("Correia", null, BigDecimal.TEN, 2, "UN", 5, PartType.PECA);

        assertThat(atMinimum.isLowStock()).isTrue();
        assertThat(belowMinimum.isLowStock()).isTrue();
    }

    @Test
    void isLowStock_whenStockAboveMinimum_shouldBeFalse() {
        Part part = new Part("Vela", null, BigDecimal.TEN, 20, "UN", 5, PartType.PECA);

        assertThat(part.isLowStock()).isFalse();
    }

    @Test
    void update_shouldChangeMinimumStockAndType() {
        Part part = new Part("Filtro", "desc", new BigDecimal("10.00"), 10, "UN");

        part.update("Filtro Premium", "nova desc", new BigDecimal("12.00"), 3, "UN", 4, PartType.INSUMO);

        assertThat(part.getName()).isEqualTo("Filtro Premium");
        assertThat(part.getMinimumStock()).isEqualTo(4);
        assertThat(part.getPartType()).isEqualTo(PartType.INSUMO);
        assertThat(part.isLowStock()).isTrue();
    }
}