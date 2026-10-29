package com.tushar27x.insightshub.repository;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

// findById, existsById, save, ... are inherited from JpaRepository
public interface UsersRepository extends JpaRepository<Users, Long> {

    List<Users> findByAlertsEnabledTrue();

    // Never-synced users have last_synced_at = NULL, and "NULL < :cutoff" is not true in SQL,
    // so they need their own branch. The brackets are why this can't be a derived query name.
    @Query("""
            select u from Users u
            where u.needsReauth = false
              and (u.lastSyncedAt is null or u.lastSyncedAt < :cutoff)
            """)
    List<Users> findDueForSync(@Param("cutoff") Instant cutoff);

    /**
     * Everyone the daily sync can sync: a stored token that GitHub hasn't rejected. Ids only, because
     * each user is loaded again on the virtual thread that syncs them.
     */
    @Query("""
            select u.githubId from Users u
            where u.needsReauth = false and u.encryptedGithubToken is not null
            order by u.githubId
            """)
    List<Long> findIdsForScheduledSync();

    /**
     * Claims a sync for this user if none was started since {@code cutoff}. One UPDATE, so two
     * concurrent requests can't both win: Postgres locks the row, and the second one re-checks the
     * condition after the first commits.
     *
     * @return 1 if claimed, 0 if a sync was started too recently
     */
    @Transactional
    @Modifying
    @Query("""
            update Users u set u.syncRequestedAt = :now
            where u.githubId = :id
              and (u.syncRequestedAt is null or u.syncRequestedAt < :cutoff)
            """)
    int claimSync(@Param("id") long id, @Param("now") Instant now, @Param("cutoff") Instant cutoff);

    /**
     * Turns the digest off: the signed unsubscribe link (Phase 12). Idempotent.
     *
     * @return 0 if there's no such user (the link outlived the account), else 1
     */
    @Transactional
    @Modifying
    @Query("update Users u set u.alertsEnabled = false where u.githubId = :id")
    int disableAlerts(@Param("id") long id);

    /**
     * Stores a class change if nobody changed the class since {@code expectedChangedAt} was read: a
     * compare-and-set like {@link #claimSync}, so of two racing switches only one starts the cooldown.
     * {@code = null} is never true in SQL, so a first change ({@code expectedChangedAt == null}) has its own query.
     *
     * @param chosen null = follow the top score
     * @return 1 if stored, 0 if the class was changed in the meantime (or there's no such user)
     */
    default int changeClass(long id, Archetype chosen, Instant now, Instant expectedChangedAt) {
        return expectedChangedAt == null
                ? changeClassFirstTime(id, chosen, now)
                : changeClassSince(id, chosen, now, expectedChangedAt);
    }

    @Transactional
    @Modifying
    @Query("""
            update Users u set u.chosenArchetype = :chosen, u.archetypeChangedAt = :now
            where u.githubId = :id and u.archetypeChangedAt is null
            """)
    int changeClassFirstTime(@Param("id") long id, @Param("chosen") Archetype chosen, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("""
            update Users u set u.chosenArchetype = :chosen, u.archetypeChangedAt = :now
            where u.githubId = :id and u.archetypeChangedAt = :expected
            """)
    int changeClassSince(@Param("id") long id, @Param("chosen") Archetype chosen, @Param("now") Instant now,
                         @Param("expected") Instant expected);
}
