package br.com.oficina.domain.model;

import java.time.LocalDateTime;

/**
 * Cliente da oficina — modelo de domínio puro, sem dependência de framework de
 * persistência. A tradução para a tabela {@code clients} é feita pelo
 * {@code ClientEntity}/{@code ClientMapper} na camada de infraestrutura.
 */
public class Client {

    private Long id;
    private String name;
    private String cpfCnpj;
    private ClientType clientType;
    private String email;
    private String phone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Client(String name, String cpfCnpj, ClientType clientType, String email, String phone) {
        this.name = name;
        this.cpfCnpj = cpfCnpj;
        this.clientType = clientType;
        this.email = email;
        this.phone = phone;
    }

    private Client() {
        // Reconstrução via rehydrate().
    }

    /** Reconstrói o cliente a partir da persistência (uso exclusivo do mapper). */
    public static Client rehydrate(Long id, String name, String cpfCnpj, ClientType clientType,
                                   String email, String phone, LocalDateTime createdAt, LocalDateTime updatedAt) {
        Client c = new Client();
        c.id = id;
        c.name = name;
        c.cpfCnpj = cpfCnpj;
        c.clientType = clientType;
        c.email = email;
        c.phone = phone;
        c.createdAt = createdAt;
        c.updatedAt = updatedAt;
        return c;
    }

    public void update(String name, String cpfCnpj, ClientType clientType, String email, String phone) {
        this.name = name;
        this.cpfCnpj = cpfCnpj;
        this.clientType = clientType;
        this.email = email;
        this.phone = phone;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCpfCnpj() { return cpfCnpj; }
    public ClientType getClientType() { return clientType; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
