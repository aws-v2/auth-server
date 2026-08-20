error id: file://<WORKSPACE>/src/main/java/org/serwin/auth_server/util/SystemUserInitializer.java:_empty_/User#builder#email#password#
file://<WORKSPACE>/src/main/java/org/serwin/auth_server/util/SystemUserInitializer.java
empty definition using pc, found symbol in pc: _empty_/User#builder#email#password#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 1423
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

        if (userRepository.existsById(systemUserUUID)) {
            log.info("[system-user] already exists");
            return;
        }

        User user = User.builder()
                // .id(systemUserUUID)
                .email(systemUserEmail)
                .pa@@ssword(passwordEncoder.encode(systemUserPassword))
                .role(Role.SYSTEM)
                .enabled(true)
                .emailVerified(true)
                .build();

        userRepository.save(user);

        log.info("[system-user] created");
    }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/User#builder#email#password#