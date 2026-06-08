package br.com.oficina.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppUserTest {

    @Test
    void constructor_shouldSetFieldsAndBeActive() {
        AppUser user = new AppUser("joao", "hashed-pass", "ADMIN");

        assertThat(user.getUsername()).isEqualTo("joao");
        assertThat(user.getPassword()).isEqualTo("hashed-pass");
        assertThat(user.getRole()).isEqualTo("ADMIN");
        assertThat(user.isActive()).isTrue();
        assertThat(user.getActive()).isTrue();
        assertThat(user.getId()).isNull();
        assertThat(user.getCreatedAt()).isNull();
    }

    @Test
    void deactivate_thenActivate_shouldToggleActive() {
        AppUser user = new AppUser("ana", "h", "MECHANIC");

        user.deactivate();
        assertThat(user.isActive()).isFalse();

        user.activate();
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void changePassword_shouldReplaceHash() {
        AppUser user = new AppUser("ana", "old-hash", "MECHANIC");

        user.changePassword("new-hash");

        assertThat(user.getPassword()).isEqualTo("new-hash");
    }
}
