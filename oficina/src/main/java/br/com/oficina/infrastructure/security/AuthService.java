package br.com.oficina.infrastructure.security;

import br.com.oficina.application.dto.LoginRequestDto;
import br.com.oficina.application.dto.LoginResponseDto;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotAuthorizedException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class);
    private static final String INVALID_CREDENTIALS = "Credenciais inválidas";
    // Hash dummy para comparação em tempo constante quando o usuário não existe
    private static final String DUMMY_HASH =
        "$2a$10$7EqJtq98hPqEX7fNZaFWoO9vQKPdN0nW1c6jE8fXE9rH3gYxQ3s2u";

    AppUserRepository userRepository;

    public AuthService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @ConfigProperty(name = "app.jwt.expiration-hours", defaultValue = "8")
    long expirationHours;

    @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "oficina-api")
    String issuer;

    public LoginResponseDto login(LoginRequestDto request) {
        Optional<AppUser> userOpt = userRepository.findByUsername(request.username());

        // Comparação em tempo constante evita timing attacks que vazam usuários válidos
        boolean validPassword = BcryptUtil.matches(
            request.password(),
            userOpt.map(u -> u.password).orElse(DUMMY_HASH)
        );

        if (userOpt.isEmpty() || !validPassword || !Boolean.TRUE.equals(userOpt.get().active)) {
            LOG.warnf("Tentativa de login inválida para usuário=%s", request.username());
            throw new NotAuthorizedException(INVALID_CREDENTIALS, "Bearer");
        }

        AppUser user = userOpt.get();
        String token = Jwt.issuer(issuer)
            .subject(user.username)
            .groups(Set.of(user.role))
            .expiresIn(Duration.ofHours(expirationHours))
            .sign();

        return new LoginResponseDto(token, user.username, user.role, expirationHours * 3600);
    }

    @Transactional
    public void createUser(String username, String rawPassword, String role) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Usuário já existe: " + username);
        }
        AppUser user = new AppUser();
        user.username = username;
        user.password = BcryptUtil.bcryptHash(rawPassword);
        user.role = role;
        user.active = true;
        userRepository.persist(user);
    }
}
