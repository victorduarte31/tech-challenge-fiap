package br.com.oficina.infrastructure.persistence;

import br.com.oficina.domain.model.Part;
import br.com.oficina.domain.model.PartType;
import br.com.oficina.testsupport.DomainTestFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a tradução {@code PartEntity} ⇄ {@code Part}, incluindo os campos de
 * controle ({@code version}, {@code active}) que só existem por causa da
 * persistência (concorrência otimista e soft-delete).
 */
class PartMapperTest {

    private final PartMapper mapper = new PartMapper();

    @Test
    void toDomain_shouldMapAllFields() {
        LocalDateTime now = LocalDateTime.now();
        PartEntity e = new PartEntity();
        DomainTestFixtures.setId(e, 3L);
        e.setName("Óleo 5W30");
        e.setDescription("Sintético");
        e.setUnitPrice(new BigDecimal("45.90"));
        e.setStockQuantity(10);
        e.setUnit("L");
        e.setMinimumStock(2);
        e.setPartType(PartType.INSUMO);
        e.setActive(true);
        DomainTestFixtures.setField(e, "version", 5L);
        DomainTestFixtures.setField(e, "createdAt", now);
        DomainTestFixtures.setField(e, "updatedAt", now);

        Part d = mapper.toDomain(e);

        assertThat(d.getId()).isEqualTo(3L);
        assertThat(d.getName()).isEqualTo("Óleo 5W30");
        assertThat(d.getDescription()).isEqualTo("Sintético");
        assertThat(d.getUnitPrice()).isEqualByComparingTo("45.90");
        assertThat(d.getStockQuantity()).isEqualTo(10);
        assertThat(d.getUnit()).isEqualTo("L");
        assertThat(d.getMinimumStock()).isEqualTo(2);
        assertThat(d.getPartType()).isEqualTo(PartType.INSUMO);
        assertThat(d.getActive()).isTrue();
        assertThat(d.getVersion()).isEqualTo(5L);
        assertThat(d.getCreatedAt()).isEqualTo(now);
        assertThat(d.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void toNewEntity_shouldCopyBusinessFields_leavingIdentityAndVersionToJpa() {
        Part d = Part.rehydrate(50L, "Filtro", "Filtro de ar", new BigDecimal("30.00"),
            7, "UN", 1, PartType.PECA, false, 9L, LocalDateTime.now(), LocalDateTime.now());

        PartEntity e = mapper.toNewEntity(d);

        assertThat(e.getName()).isEqualTo("Filtro");
        assertThat(e.getDescription()).isEqualTo("Filtro de ar");
        assertThat(e.getUnitPrice()).isEqualByComparingTo("30.00");
        assertThat(e.getStockQuantity()).isEqualTo(7);
        assertThat(e.getUnit()).isEqualTo("UN");
        assertThat(e.getMinimumStock()).isEqualTo(1);
        assertThat(e.getPartType()).isEqualTo(PartType.PECA);
        assertThat(e.getActive()).isFalse();
        // Identidade e versão são geradas/controladas pelo JPA, não pelo mapper.
        assertThat(e.getId()).isNull();
        assertThat(e.getVersion()).isNull();
    }

    @Test
    void roundTrip_shouldPreserveBusinessFields() {
        PartEntity source = new PartEntity();
        source.setName("Pastilha");
        source.setDescription(null);
        source.setUnitPrice(new BigDecimal("120.50"));
        source.setStockQuantity(4);
        source.setUnit("PAR");
        source.setMinimumStock(1);
        source.setPartType(PartType.PECA);
        source.setActive(true);

        PartEntity back = mapper.toNewEntity(mapper.toDomain(source));

        assertThat(back.getName()).isEqualTo(source.getName());
        assertThat(back.getUnitPrice()).isEqualByComparingTo(source.getUnitPrice());
        assertThat(back.getStockQuantity()).isEqualTo(source.getStockQuantity());
        assertThat(back.getUnit()).isEqualTo(source.getUnit());
        assertThat(back.getMinimumStock()).isEqualTo(source.getMinimumStock());
        assertThat(back.getPartType()).isEqualTo(source.getPartType());
        assertThat(back.getActive()).isEqualTo(source.getActive());
    }
}
