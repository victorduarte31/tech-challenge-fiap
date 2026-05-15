package br.com.oficina.infrastructure.security;

import br.com.oficina.application.dto.LoginRequestDto;
import br.com.oficina.application.dto.LoginResponseDto;
import br.com.oficina.testsupport.DomainTestFixtures;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class AuthServiceTest {

    @Inject
    AuthService authService;

    @InjectMock
    AppUserRepository appUserRepository;

    @Test
    void login_withValidCredentials_shouldReturnToken() {
        AppUser user = new AppUser("admin", BcryptUtil.bcryptHash("admin123"), "ADMIN");
        DomainTestFixtures.setId(user, 1L);

        when(appUserRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        LoginRequestDto request = new LoginRequestDto("admin", "admin123");
        LoginResponseDto response = authService.login(request);

        assertThat(response.token()).isNotBlank();
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.expiresIn()).isGreaterThan(0);
    }

    @Test
    void login_withWrongPassword_shouldThrowNotAuthorized() {
        AppUser user = new AppUser("admin", BcryptUtil.bcryptHash("admin123"), "ADMIN");

        when(appUserRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        LoginRequestDto request = new LoginRequestDto("admin", "wrongpassword");

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(NotAuthorizedException.class)
            .hasMessageContaining("Credenciais inválidas");
    }

    @Test
    void login_withUnknownUser_shouldThrowNotAuthorized() {
        when(appUserRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        LoginRequestDto request = new LoginRequestDto("unknown", "any");

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void login_withInactiveUser_shouldThrowNotAuthorized() {
        AppUser user = new AppUser("admin", BcryptUtil.bcryptHash("admin123"), "ADMIN");
        user.deactivate();

        when(appUserRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        LoginRequestDto request = new LoginRequestDto("admin", "admin123");

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(NotAuthorizedException.class);
    }
}
