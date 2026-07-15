package br.com.oficina.infrastructure.persistence;

import br.com.oficina.domain.model.Vehicle;
import br.com.oficina.testsupport.DomainTestFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Testa a tradução {@code VehicleEntity} ⇄ {@code Vehicle}. Ao contrário dos
 * demais mappers, este depende do {@code EntityManager}: a FK do cliente é
 * resolvida por proxy ({@code em.getReference}) na volta ao entity — por isso o
 * mock. O nome do cliente é projeção de leitura obtida do lado da entity.
 */
@ExtendWith(MockitoExtension.class)
class VehicleMapperTest {

    @Mock
    EntityManager em;

    @InjectMocks
    VehicleMapper mapper;

    @Test
    void toDomain_shouldMapFieldsIncludingClientProjection() {
        LocalDateTime now = LocalDateTime.now();
        ClientEntity owner = new ClientEntity();
        DomainTestFixtures.setId(owner, 10L);
        owner.setName("Dono do Carro");

        VehicleEntity e = new VehicleEntity();
        DomainTestFixtures.setId(e, 2L);
        e.setLicensePlate("ABC1D23");
        e.setBrand("Toyota");
        e.setModel("Corolla");
        e.setProductionYear(2020);
        e.setClient(owner);
        DomainTestFixtures.setField(e, "createdAt", now);
        DomainTestFixtures.setField(e, "updatedAt", now);

        Vehicle d = mapper.toDomain(e);

        assertThat(d.getId()).isEqualTo(2L);
        assertThat(d.getLicensePlate()).isEqualTo("ABC1D23");
        assertThat(d.getBrand()).isEqualTo("Toyota");
        assertThat(d.getModel()).isEqualTo("Corolla");
        assertThat(d.getProductionYear()).isEqualTo(2020);
        assertThat(d.getClientId()).isEqualTo(10L);
        assertThat(d.getClientName()).isEqualTo("Dono do Carro");
        assertThat(d.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void toNewEntity_shouldResolveClientFkByReference() {
        ClientEntity ref = new ClientEntity();
        DomainTestFixtures.setId(ref, 10L);
        when(em.getReference(ClientEntity.class, 10L)).thenReturn(ref);

        Vehicle d = Vehicle.rehydrate(99L, "XYZ9A88", "Honda", "Civic", 2021, 10L,
            "Dono", LocalDateTime.now(), LocalDateTime.now());

        VehicleEntity e = mapper.toNewEntity(d);

        assertThat(e.getLicensePlate()).isEqualTo("XYZ9A88");
        assertThat(e.getBrand()).isEqualTo("Honda");
        assertThat(e.getModel()).isEqualTo("Civic");
        assertThat(e.getProductionYear()).isEqualTo(2021);
        assertThat(e.getClient()).isSameAs(ref);
        // Identidade e timestamps ficam a cargo do JPA.
        assertThat(e.getId()).isNull();
        assertThat(e.getCreatedAt()).isNull();
    }

    @Test
    void applyState_shouldReassignClientReferenceFromDomainClientId() {
        ClientEntity ref = new ClientEntity();
        DomainTestFixtures.setId(ref, 20L);
        when(em.getReference(ClientEntity.class, 20L)).thenReturn(ref);

        VehicleEntity managed = new VehicleEntity();
        Vehicle d = Vehicle.rehydrate(1L, "AAA1A11", "Fiat", "Uno", 2015, 20L,
            "Outro Dono", LocalDateTime.now(), LocalDateTime.now());

        mapper.applyState(managed, d);

        assertThat(managed.getClient()).isSameAs(ref);
        assertThat(managed.getLicensePlate()).isEqualTo("AAA1A11");
    }
}
