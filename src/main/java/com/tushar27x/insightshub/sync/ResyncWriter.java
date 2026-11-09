package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.sync.SyncService.PreparedResync;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * B11: the writes of a {@link PreparedResync}. {@code @Transactional} (REQUIRED) joins the caller's transaction, so the
 * private switch can commit the new switch value, the lock and these writes together, or roll all of them back.
 * A separate bean for the same reason as {@link InsightsWriter}: the proxy.
 */
@Component
public class ResyncWriter {

    private final ActivityWriter activityWriter;
    private final InsightsWriter insightsWriter;
    private final UsersRepository usersRepository;

    public ResyncWriter(ActivityWriter activityWriter, InsightsWriter insightsWriter, UsersRepository usersRepository) {
        this.activityWriter = activityWriter;
        this.insightsWriter = insightsWriter;
        this.usersRepository = usersRepository;
    }

    @Transactional
    public void write(PreparedResync resync) {
        long userId = resync.userId();
        activityWriter.writeCalendarInFull(userId, resync.calendar());
        // private counts must not survive turning off: when private is off now, every stored branch count is replaced
        activityWriter.replaceBranchCounts(userId, resync.branchCounts(), !resync.includePrivate());
        if (resync.texts() != null) {
            insightsWriter.save(userId, resync.stats(), resync.result(), resync.texts(), resync.at());
        } else {
            insightsWriter.saveKeepingTexts(userId, resync.stats(), resync.result(), resync.at());
        }
        usersRepository.setPrivateResyncDue(userId, false); // a full resync pays any owed one
    }
}
