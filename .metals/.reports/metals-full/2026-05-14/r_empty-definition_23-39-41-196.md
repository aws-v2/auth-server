error id: file://<WORKSPACE>/src/main/java/org/serwin/auth_server/util/SystemUserInitializer.java:_empty_/NatsService#publish#
file://<WORKSPACE>/src/main/java/org/serwin/auth_server/util/SystemUserInitializer.java
empty definition using pc, found symbol in pc: _empty_/NatsService#publish#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 2376
uri: file://<WORKSPACE>/src/main/java/org/serwin/auth_server/util/SystemUserInitializer.java
text:
```scala
package org.serwin.auth_server.util;

import org.serwin.auth_server.enums.Role;
import org.serwin.auth_server.entities.User;
import org.serwin.auth_server.repository.UserRepository;
import org.serwin.auth_server.events.SystemUserCreatedEventS3;
import org.serwin.auth_server.service.NatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SystemUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NatsService natsService;

    @Value("${system-user.email}")
    private String systemUserEmail;

    @Value("${system-user.password}")
    private String systemUserPassword;

    @Value("${system-user.id}")
    private String systemUserId;

    @Override
    public void run(ApplicationArguments args) {
        UUID systemUserUUID = UUID.fromString(systemUserId);
        log.info("Creating system user with email: {}", systemUserEmail);

        if (userRepository.existsById(systemUserUUID)) {
            log.info("[system-user] already exists");
            return;
        }

        User user = User.builder()
                .id(systemUserUUID)
                .email(systemUserEmail)
                .password(passwordEncoder.encode(systemUserPassword))
                .role(Role.SYSTEM)
                .enabled(true)
                .emailVerified(true)
                .build();

        userRepository.save(user);
        log.info("[system-user] created");

        // Publish S3 bucket creation event for system default buckets
        try {
            SystemUserCreatedEventS3 s3Event = SystemUserCreatedEventS3.builder()
                    .event_id(UUID.randomUUID())
                    .event_type("system.user.created")
                    .tenant_id(user.getId())
                    .tenant_name(user.getEmail())
                    .created_at(java.time.OffsetDateTime.now().toString())
                    .build();

            natsService.publ@@ish("s3", "system.user.created", s3Event);
            log.info("[system-user] published S3 bucket creation event for tenant: {}", user.getId());

        } catch (Exception e) {
            log.error("[system-user] failed to publish S3 event: {}", e.getMessage());
        }
    }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/NatsService#publish#