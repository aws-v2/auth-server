package org.serwin.auth_server.repository;

import org.serwin.auth_server.entities.ApiKey;
import org.serwin.auth_server.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByAccessKeyId(String accessKeyId);

    List<ApiKey> findByUserAndEnabledTrue(User user);

    List<ApiKey> findByUser(User user);

    boolean existsByAccessKeyId(String accessKeyId);
}
