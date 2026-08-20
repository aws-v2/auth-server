package org.serwin.auth_server.entities.iam;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "access_keys")
@Data
@NoArgsConstructor
public class AccessKey {

    @Id
    @Column(length = 20)
    private String accessKeyId;

    @Column(length = 40, nullable = false)
    private String secretAccessKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;



    public enum Status {
        ACTIVE, INACTIVE
    }
}
