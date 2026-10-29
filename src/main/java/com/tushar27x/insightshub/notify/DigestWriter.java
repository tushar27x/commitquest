package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.repository.DigestRepository;
import com.tushar27x.insightshub.repository.DigestRepository.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The digest's writes in one transaction. A separate bean like {@code sync.InsightsWriter}: {@code @Transactional}
 * only applies through the Spring proxy, not on a call from inside {@link DigestService}.
 */
@Slf4j
@Component
public class DigestWriter {

    private final DigestRepository digests;

    public DigestWriter(DigestRepository digests) {
        this.digests = digests;
    }

    /**
     * Records a digest and stamps its events, all or nothing: a {@code SENT} row for the mailed events (if any)
     * and a {@code SKIPPED} row for the skipped ones (if any). Only events that are still unsent are stamped.
     *
     * @param channel {@link NotificationChannel#name()} of the channel that sent {@code mailed}
     */
    @Transactional
    public void record(long userId, List<Long> mailed, String channel, boolean aiIntro, List<Long> skipped,
                       Instant now) {
        if (!mailed.isEmpty()) {
            write(userId, Status.SENT, channel, aiIntro, mailed, now);
        }
        if (!skipped.isEmpty()) {
            write(userId, Status.SKIPPED, null, false, skipped, now);
        }
    }

    private void write(long userId, Status status, String channel, boolean aiIntro, List<Long> eventIds,
                       Instant now) {
        long id = digests.insertDigest(userId, status, channel, eventIds.size(), aiIntro, now);
        int stamped = digests.stamp(id, userId, eventIds);
        if (stamped != eventIds.size()) { // another run got there first: harmless, but worth knowing
            log.warn("Digest {} for user {}: stamped {} of {} events", id, userId, stamped, eventIds.size());
        }
    }
}
