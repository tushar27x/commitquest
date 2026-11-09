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
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.time.LocalDate;


/**
 * {@code @DynamicUpdate} (B45): an UPDATE writes only the columns that changed. Without it Hibernate writes every
 * column, so a short transaction that only stamps {@code last_synced_at} or {@code needs_reauth} could write back the
 * GitHub token it had read, over the new one a login stored in the meantime.
 */
@Entity
@Table(name = "users")
@DynamicUpdate
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

    /** When {@link #encryptedGithubToken} was obtained (V8, B43): its age goes into the log of a GitHub 401. */
    @Setter
    private Instant githubTokenObtainedAt;

    /** V9 (B10): the scopes GitHub granted the stored token, comma-separated; null = never recorded. */
    @Setter
    @Column(columnDefinition = "text")
    private String githubScopes;

    /** V9 (B10): the user's choice to let insights read their private repos (needs the repo scope too). */
    @Setter
    @Column(nullable = false)
    private boolean includePrivate;

    /** V10 (B11): when the user last made an effective change to private access; starts the lock. Null = never. */
    @Setter
    private Instant privateChangedAt;

    /** V10 (B11): a full resync is owed (the one that should have followed an effective change did not happen). */
    @Setter
    @Column(nullable = false)
    private boolean privateResyncDue;

    /** Whether the recorded scopes include {@code repo}, the only scope that shows private repos. */
    public boolean hasRepoScope() {
        return GitHubScopes.hasRepo(githubScopes);
    }

    /** What a sync may fetch: the user's choice, and a token that can. */
    public boolean readsPrivate() {
        return includePrivate && hasRepoScope();
    }

    /** A login's new token, and when it was obtained. */
    public void replaceGithubToken(String encryptedToken, Instant obtainedAt) {
        this.encryptedGithubToken = encryptedToken;
        this.githubTokenObtainedAt = obtainedAt;
    }

    public Users(Long githubId, String login) {
        this.githubId = githubId;
        this.login = login;
    }
}
