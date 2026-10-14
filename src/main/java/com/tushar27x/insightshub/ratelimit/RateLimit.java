package com.tushar27x.insightshub.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits how often one client may call a controller method. The numbers live in
 * {@code insightshub.rate-limit.policies.<policy>} so they can change without a rebuild;
 * every policy used here must exist there (checked at startup).
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Name of the policy in {@code insightshub.rate-limit.policies}. */
    String policy();

    /** Who gets their own counter. */
    KeyType key();

    enum KeyType {
        /** The connection's IP: for endpoints called before login. */
        IP,
        /** The JWT subject (GitHub id): for signed-in endpoints, so users behind one IP don't share a limit. */
        USER
    }
}
