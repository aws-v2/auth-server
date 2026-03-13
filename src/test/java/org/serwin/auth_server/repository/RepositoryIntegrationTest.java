package org.serwin.auth_server.repository;

import org.junit.jupiter.api.Test;
import org.serwin.auth_server.entities.PasswordResetToken;
import org.serwin.auth_server.entities.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class RepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate"); // Test Flyway migrations
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Test
    void userRepository_saveAndFind_success() {
        User user = User.builder()
                .email("repo-test@example.com")
                .password("encodedPassword")
                .build();

        User savedUser = userRepository.save(user);

        assertThat(savedUser.getId()).isNotNull();
        Optional<User> foundUser = userRepository.findByEmail("repo-test@example.com");
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("repo-test@example.com");
    }

    @Test
    void tokenRepository_saveAndDeleteByUser_success() {
        User user = User.builder()
                .email("token-test@example.com")
                .password("password")
                .build();
        user = userRepository.save(user);

        PasswordResetToken token = PasswordResetToken.builder()
                .token("test-token-123")
                .user(user)
                .expiryDate(LocalDateTime.now().plusHours(1))
                .build();

        tokenRepository.save(token);

        assertThat(tokenRepository.findByToken("test-token-123")).isPresent();

        tokenRepository.deleteByUser(user);

        assertThat(tokenRepository.findByToken("test-token-123")).isEmpty();
    }
}
