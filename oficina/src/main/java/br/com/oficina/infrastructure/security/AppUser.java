package br.com.oficina.infrastructure.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AppUser() {
        // Required by JPA
    }

    public AppUser(String username, String hashedPassword, String role) {
        this.username = username;
        this.password = hashedPassword;
        this.role = role;
        this.active = true;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public void changePassword(String newHashedPassword) {
        this.password = newHashedPassword;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getRole() { return role; }
    public Boolean getActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
