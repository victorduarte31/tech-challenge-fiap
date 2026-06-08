package br.com.oficina.infrastructure.security;

import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    AppUserRepository userRepository;

    @InjectMocks
    DataSeeder dataSeeder;

    @BeforeEach
    void setUp() {
        // @ConfigProperty não é injetado pelo Mockito; preenche os campos package-private
        dataSeeder.adminUsername = "admin";
        dataSeeder.mechanicUsername = "mecanico";
        dataSeeder.adminPassword = Optional.of("admin123");
        dataSeeder.mechanicPassword = Optional.of("mecanico123");
        dataSeeder.seedEnabled = true;
    }

    @Test
    void onStart_whenSeedDisabled_shouldNotPersistAnyUser() {
        dataSeeder.seedEnabled = false;

        dataSeeder.onStart(new StartupEvent());

        verify(userRepository, never()).persist(any(AppUser.class));
    }

    @Test
    void onStart_whenUsersDoNotExist_shouldCreateAdminAndMechanic() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        dataSeeder.onStart(new StartupEvent());

        verify(userRepository, times(2)).persist(any(AppUser.class));
    }

    @Test
    void onStart_whenUsersAlreadyExist_shouldNotPersist() {
        AppUser existing = new AppUser("admin", "hash", "ADMIN");
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(existing));

        dataSeeder.onStart(new StartupEvent());

        verify(userRepository, never()).persist(any(AppUser.class));
    }

    @Test
    void onStart_whenPasswordBlankOrEmpty_shouldGenerateRandomAndSeed() {
        dataSeeder.adminPassword = Optional.of("   "); // em branco -> gera aleatória
        dataSeeder.mechanicPassword = Optional.empty(); // ausente -> gera aleatória
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        dataSeeder.onStart(new StartupEvent());

        verify(userRepository, times(2)).persist(any(AppUser.class));
    }
}
