package br.com.oficina.infrastructure.persistence;

import br.com.oficina.domain.model.ServiceItem;
import br.com.oficina.testsupport.DomainTestFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a tradução {@code ServiceItemEntity} ⇄ {@code ServiceItem}.
 */
class ServiceItemMapperTest {

    private final ServiceItemMapper mapper = new ServiceItemMapper();

    @Test
    void toDomain_shouldMapAllFields() {
        LocalDateTime now = LocalDateTime.now();
        ServiceItemEntity e = new ServiceItemEntity();
        DomainTestFixtures.setId(e, 4L);
        e.setName("Troca de Óleo");
        e.setDescription("Inclui filtro");
        e.setBasePrice(new BigDecimal("120.00"));
        e.setEstimatedDurationMinutes(30);
        e.setActive(true);
        DomainTestFixtures.setField(e, "createdAt", now);
        DomainTestFixtures.setField(e, "updatedAt", now);

        ServiceItem d = mapper.toDomain(e);

        assertThat(d.getId()).isEqualTo(4L);
        assertThat(d.getName()).isEqualTo("Troca de Óleo");
        assertThat(d.getDescription()).isEqualTo("Inclui filtro");
        assertThat(d.getBasePrice()).isEqualByComparingTo("120.00");
        assertThat(d.getEstimatedDurationMinutes()).isEqualTo(30);
        assertThat(d.getActive()).isTrue();
        assertThat(d.getCreatedAt()).isEqualTo(now);
        assertThat(d.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void toNewEntity_shouldCopyBusinessFields_leavingIdentityToJpa() {
        ServiceItem d = ServiceItem.rehydrate(80L, "Alinhamento", null,
            new BigDecimal("90.00"), 45, false, LocalDateTime.now(), LocalDateTime.now());

        ServiceItemEntity e = mapper.toNewEntity(d);

        assertThat(e.getName()).isEqualTo("Alinhamento");
        assertThat(e.getBasePrice()).isEqualByComparingTo("90.00");
        assertThat(e.getEstimatedDurationMinutes()).isEqualTo(45);
        assertThat(e.getActive()).isFalse();
        assertThat(e.getId()).isNull();
        assertThat(e.getCreatedAt()).isNull();
    }

    @Test
    void roundTrip_shouldPreserveBusinessFields() {
        ServiceItemEntity source = new ServiceItemEntity();
        source.setName("Balanceamento");
        source.setDescription("4 rodas");
        source.setBasePrice(new BigDecimal("60.00"));
        source.setEstimatedDurationMinutes(20);
        source.setActive(true);

        ServiceItemEntity back = mapper.toNewEntity(mapper.toDomain(source));

        assertThat(back.getName()).isEqualTo(source.getName());
        assertThat(back.getDescription()).isEqualTo(source.getDescription());
        assertThat(back.getBasePrice()).isEqualByComparingTo(source.getBasePrice());
        assertThat(back.getEstimatedDurationMinutes()).isEqualTo(source.getEstimatedDurationMinutes());
        assertThat(back.getActive()).isEqualTo(source.getActive());
    }
}
