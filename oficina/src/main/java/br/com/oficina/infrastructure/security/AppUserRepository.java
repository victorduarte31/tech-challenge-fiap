package br.com.oficina.infrastructure.security;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class AppUserRepository implements PanacheRepository<AppUser> {

    public Optional<AppUser> findByUsername(String username) {
        return find("username = ?1 and active = true", username).firstResultOptional();
    }
}
