package com.tushar27x.insightshub.repository;

import com.tushar27x.insightshub.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
