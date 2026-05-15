package br.com.oficina.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "cpf_cnpj", nullable = false, unique = true, length = 14)
    private String cpfCnpj;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false, length = 2)
    private ClientType clientType;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private final List<Vehicle> vehicles = new ArrayList<>();

    protected Client() {
        // Required by JPA
    }

    public Client(String name, String cpfCnpj, ClientType clientType, String email, String phone) {
        this.name = name;
        this.cpfCnpj = cpfCnpj;
        this.clientType = clientType;
        this.email = email;
        this.phone = phone;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
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
    public List<Vehicle> getVehicles() { return vehicles; }
}
