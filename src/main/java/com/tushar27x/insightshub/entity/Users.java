package com.tushar27x.insightshub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;


@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Users {
    @Id
    private Long githubId;

    @Setter
    @Column(nullable = false, length = 39)
    private String login;

    @Setter
    @Column(length=320)
    private String email;

    @Setter
    @Column(columnDefinition = "text")
    private String encryptedGithubToken;

    @Setter
    @Column(nullable = false)
    private boolean alertsEnabled = false;

    @Setter
    @Column(nullable = false)
    private int noCommitDays = 3;

    @Setter
    @Column(nullable = false)
    private boolean publicProfile;

    @Setter
    @Column( nullable = false)
    private boolean needsReauth;

    @Setter
    private Instant lastSyncedAt;

    @Column(nullable = false, updatable = false, insertable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    public Users(Long githubId, String login) {
        this.githubId = githubId;
        this.login = login;
    }
}
