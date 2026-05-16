error id: file://<WORKSPACE>/src/main/java/org/serwin/auth_server/util/SystemUserInitializer.java:_empty_/user#getEmail#
file://<WORKSPACE>/src/main/java/org/serwin/auth_server/util/SystemUserInitializer.java
empty definition using pc, found symbol in pc: _empty_/user#getEmail#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 2961
uri: file://<WORKSPACE>/src/main/java/org/serwin/auth_server/util/SystemUserInitializer.java
text:
```scala
package org.serwin.auth_server.util;

import org.serwin.auth_server.enums.Role;
import org.serwin.auth_server.entities.User;
import org.serwin.auth_server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

@Component
@RequiredArgsConstructor
@Slf4j
public class SystemUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${system-user.email}")
    private String systemUserEmail;

    @Value("${system-user.password}")
    private String systemUserPassword;

    @Value("${system-user.id}")
    private String systemUserId;

    @Override
    public void run(ApplicationArguments args) {
        UUID systemUserUUID = UUID.fromString(systemUserId); // ✅ parsed after @Value injection
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
        after creating the sys useruser iwantto sendanats message to s3 to create default buckets, ie libvirt-templates-system and agent-binary-system buckes,

        so 


        @Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemUserCreatedEventS3 {
    private UUID event_id;
    private String event_type;
    private UUID tenant_id;
    private String tenant_name;
    private String created_at; // RFC3339 timestamp
}



this si howtosend 

        // Publish Registration Events to NATS
        try {
            // 1. Publish TenantCreated event (formal)
            TenantCreatedEvent tenantEvent = TenantCreatedEvent.builder()
                    .event_id(UUID.randomUUID())
                    .event_type("tenant.created")
                    .tenant_id(user.getId())
                    .tenant_name(user.getEmail())
                    .created_at(java.time.OffsetDateTime.now().toString())
                    .build();
 
            // 2. Publish UserRegistered event (standard/example)
            natsService.publish("user", "registered", Map.of(
                    "tenant_name", user.getEmail().split("@")[0],
                    "tenant_email", user.getEma@@il(),
                    "tenant_id", user.getId().toString(),
                    "timestamp", java.time.OffsetDateTime.now().toString()));
            log.info("Published user.registered event for user: {}", user.getEmail());




        } catch (Exception e) {
            log.error("Failed to publish registration events to NATS: {}", e.getMessage());
        }
    }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/user#getEmail#