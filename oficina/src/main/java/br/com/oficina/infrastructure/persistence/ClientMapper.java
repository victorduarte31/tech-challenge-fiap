package br.com.oficina.infrastructure.persistence;

import br.com.oficina.domain.model.Client;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Traduz entre o modelo de domínio {@code Client} (puro) e a entidade de
 * persistência {@code ClientEntity}.
 */
@ApplicationScoped
public class ClientMapper {

    public Client toDomain(ClientEntity e) {
        return Client.rehydrate(
            e.getId(), e.getName(), e.getCpfCnpj(), e.getClientType(),
            e.getEmail(), e.getPhone(), e.getCreatedAt(), e.getUpdatedAt());
    }

    public ClientEntity toNewEntity(Client d) {
        ClientEntity e = new ClientEntity();
        applyState(e, d);
        return e;
    }

    /** Sincroniza o estado do domínio na entidade gerenciada. */
    public void applyState(ClientEntity e, Client d) {
        e.setName(d.getName());
        e.setCpfCnpj(d.getCpfCnpj());
        e.setClientType(d.getClientType());
        e.setEmail(d.getEmail());
        e.setPhone(d.getPhone());
    }
}
