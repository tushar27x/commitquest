package com.tushar27x.insightshub.notify;

/**
 * Where a digest goes (plan §12's Strategy): email now, Discord later as a second implementation.
 */
public interface NotificationChannel {

    /** Stored in {@code digests.channel}, at most 16 characters. */
    String name();

    /**
     * Delivers the digest, or throws. Returning means the provider accepted it: only then is it marked sent.
     *
     * @throws NotificationException if it wasn't delivered (it's retried on a later run)
     */
    void send(Digest digest);
}
