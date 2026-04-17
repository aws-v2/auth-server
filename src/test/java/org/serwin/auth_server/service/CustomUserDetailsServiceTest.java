package org.serwin.auth_server.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.serwin.auth_server.entities.User;
import org.serwin.auth_server.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    // ── helper ────────────────────────────────────────────────────────────────

    private User buildUser(String email, String encodedPassword) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setPassword(encodedPassword);
        return u;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // loadUserByUsername
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class LoadUserByUsername {

        @Test
        void success_returnsUserDetailsWithCorrectEmail() {
            User user = buildUser("test@example.com", "encoded-pw");
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            UserDetails details = customUserDetailsService.loadUserByUsername("test@example.com");

            assertThat(details.getUsername()).isEqualTo("test@example.com");
        }

        @Test
        void success_returnsUserDetailsWithCorrectPassword() {
            User user = buildUser("test@example.com", "$2a$12$hashedPassword");
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            UserDetails details = customUserDetailsService.loadUserByUsername("test@example.com");

            assertThat(details.getPassword()).isEqualTo("$2a$12$hashedPassword");
        }

        @Test
        void success_returnsEmptyAuthoritiesList() {
            // The service always constructs a User with new ArrayList<>() — no roles
            User user = buildUser("test@example.com", "pw");
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            UserDetails details = customUserDetailsService.loadUserByUsername("test@example.com");

            assertThat(details.getAuthorities()).isEmpty();
        }

        @Test
        void success_returnedUserDetailsIsEnabledByDefault() {
            // Spring's User object sets enabled=true when no extra flags are passed
            User user = buildUser("test@example.com", "pw");
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            UserDetails details = customUserDetailsService.loadUserByUsername("test@example.com");

            assertThat(details.isEnabled()).isTrue();
            assertThat(details.isAccountNonExpired()).isTrue();
            assertThat(details.isAccountNonLocked()).isTrue();
            assertThat(details.isCredentialsNonExpired()).isTrue();
        }

        @Test
        void userNotFound_throwsUsernameNotFoundException() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("ghost@example.com"))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("ghost@example.com");
        }

        @Test
        void userNotFound_exceptionMessageContainsEmail() {
            when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("missing@example.com"))
                    .hasMessage("User not found with email: missing@example.com");
        }

        @Test
        void delegatesToRepositoryWithExactEmail() {
            User user = buildUser("exact@example.com", "pw");
            when(userRepository.findByEmail("exact@example.com")).thenReturn(Optional.of(user));

            customUserDetailsService.loadUserByUsername("exact@example.com");

            verify(userRepository, times(1)).findByEmail("exact@example.com");
            verifyNoMoreInteractions(userRepository);
        }
    }
}