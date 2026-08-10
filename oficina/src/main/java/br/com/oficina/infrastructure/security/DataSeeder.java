package br.com.oficina.infrastructure.security;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@ApplicationScoped
public class DataSeeder {

    private static final Logger LOG = Logger.getLogger(DataSeeder.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    AppUserRepository userRepository;

    public DataSeeder(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @ConfigProperty(name = "app.seed.admin-username", defaultValue = "admin")
    String adminUsername;

    @ConfigProperty(name = "app.seed.admin-password")
    Optional<String> adminPassword;

    @ConfigProperty(name = "app.seed.mechanic-username", defaultValue = "mecanico")
    String mechanicUsername;

    @ConfigProperty(name = "app.seed.mechanic-password")
    Optional<String> mechanicPassword;

    @ConfigProperty(name = "app.seed.enabled", defaultValue = "true")
    boolean seedEnabled;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        if (!seedEnabled) {
            LOG.info("Seed inicial desabilitado (app.seed.enabled=false).");
            return;
        }
        seedUser(adminUsername, adminPassword, "ADMIN");
        seedUser(mechanicUsername, mechanicPassword, "MECHANIC");
    }

    private void seedUser(String username, Optional<String> configuredPassword, String role) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        String password = configuredPassword.filter(p -> !p.isBlank()).orElseGet(() -> {
            String generated = generateRandomPassword();
            // Trade-off consciente: a senha vai para o log (e daí para o stdout do pod
            // e qualquer coletor a jusante). É o preço de não ter um gerenciador de
            // segredos no escopo do desafio — sem isto, um ambiente novo ficaria sem
            // nenhuma credencial utilizável. O caminho recomendado é sempre definir
            // APP_SEED_*_PASSWORD via Secret e desligar o seed (APP_SEED_ENABLED=false)
            // após o primeiro start; aí este ramo nunca executa.
            LOG.warnf("[SEED] Senha não configurada para '%s'. Senha gerada (anote, altere e "
                    + "desabilite o seed): %s", username, generated);
            return generated;
        });

        AppUser user = new AppUser(username, BcryptUtil.bcryptHash(password), role);
        userRepository.persist(user);
        LOG.infof("Usuário inicial criado: %s (%s)", username, role);
    }

    private static String generateRandomPassword() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
