package com.tushar27x.insightshub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.time.LocalDate;


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

    /** Start of the last sync attempt; set atomically by {@code UsersRepository.claimSync}. */
    @Setter
    private Instant syncRequestedAt;

    @Column(nullable = false, updatable = false, insertable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    // --- V5: the hero's settings ---

    /** IANA zone the user's days are counted in; set from the browser (PUT /api/user/preferences). */
    @Setter
    @Column(nullable = false, length = 64)
    private String timezone = "UTC";

    /** Weekday bitmask ({@code hero.RestDays}): bit 0 = Monday ... bit 6 = Sunday, at most 2 set. */
    @Setter
    @Column(nullable = false)
    private short restDays;

    /** The mask that applied before {@link #restDaysFrom}. */
    @Setter
    @Column(nullable = false)
    private short restDaysPrevious;

    /** First local day {@link #restDays} applies; null if the rest days were never changed. */
    @Setter
    private LocalDate restDaysFrom;

    /** Null: follow the top-scored class. */
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Archetype chosenArchetype;

    /** Starts the class-switch cooldown. */
    @Setter
    private Instant archetypeChangedAt;

    public Users(Long githubId, String login) {
        this.githubId = githubId;
        this.login = login;
    }
}
