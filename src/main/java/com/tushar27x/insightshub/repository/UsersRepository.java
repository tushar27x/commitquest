package com.tushar27x.insightshub.repository;

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
}
