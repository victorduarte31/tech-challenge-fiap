package br.com.oficina.infrastructure.persistence;

import br.com.oficina.domain.model.Client;
import br.com.oficina.domain.model.ClientType;
import br.com.oficina.testsupport.DomainTestFixtures;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a tradução {@code ClientEntity} ⇄ {@code Client}. Mapper puro (sem CDI):
 * instanciado diretamente. Identidade e timestamps são gerenciados pelo JPA
 * ({@code @PrePersist}), por isso {@code toNewEntity} não os copia — o teste
 * documenta esse limite explicitamente.
 */
class ClientMapperTest {

    private final ClientMapper mapper = new ClientMapper();

    @Test
    void toDomain_shouldMapAllFields() {
        LocalDateTime now = LocalDateTime.now();
        ClientEntity e = new ClientEntity();
        DomainTestFixtures.setId(e, 7L);
        e.setName("Maria Silva");
        e.setCpfCnpj("11144477735");
        e.setClientType(ClientType.PF);
        e.setEmail("maria@x.com");
        e.setPhone("11999998888");
        DomainTestFixtures.setField(e, "createdAt", now);
        DomainTestFixtures.setField(e, "updatedAt", now);

        Client d = mapper.toDomain(e);

        assertThat(d.getId()).isEqualTo(7L);
        assertThat(d.getName()).isEqualTo("Maria Silva");
        assertThat(d.getCpfCnpj()).isEqualTo("11144477735");
        assertThat(d.getClientType()).isEqualTo(ClientType.PF);
        assertThat(d.getEmail()).isEqualTo("maria@x.com");
        assertThat(d.getPhone()).isEqualTo("11999998888");
        assertThat(d.getCreatedAt()).isEqualTo(now);
        assertThat(d.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void toNewEntity_shouldCopyBusinessFields_leavingIdentityToJpa() {
        Client d = Client.rehydrate(99L, "João", "52998224725", ClientType.PF,
            "joao@x.com", "1130001111", LocalDateTime.now(), LocalDateTime.now());

        ClientEntity e = mapper.toNewEntity(d);

        assertThat(e.getName()).isEqualTo("João");
        assertThat(e.getCpfCnpj()).isEqualTo("52998224725");
        assertThat(e.getClientType()).isEqualTo(ClientType.PF);
        assertThat(e.getEmail()).isEqualTo("joao@x.com");
        assertThat(e.getPhone()).isEqualTo("1130001111");
        // Identidade e timestamps não são responsabilidade do mapper (JPA os gera).
        assertThat(e.getId()).isNull();
        assertThat(e.getCreatedAt()).isNull();
        assertThat(e.getUpdatedAt()).isNull();
    }

    @Test
    void roundTrip_shouldPreserveBusinessFields() {
        ClientEntity source = new ClientEntity();
        source.setName("Empresa X");
        source.setCpfCnpj("11222333000181");
        source.setClientType(ClientType.PJ);
        source.setEmail("contato@empresa.com");
        source.setPhone("1140004000");

        ClientEntity back = mapper.toNewEntity(mapper.toDomain(source));

        assertThat(back.getName()).isEqualTo(source.getName());
        assertThat(back.getCpfCnpj()).isEqualTo(source.getCpfCnpj());
        assertThat(back.getClientType()).isEqualTo(source.getClientType());
        assertThat(back.getEmail()).isEqualTo(source.getEmail());
        assertThat(back.getPhone()).isEqualTo(source.getPhone());
    }
}
