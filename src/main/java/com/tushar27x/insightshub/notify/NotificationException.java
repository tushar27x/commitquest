package com.tushar27x.insightshub.notify;

/** A channel couldn't deliver a digest. Never carries the address or the message body. */
public class NotificationException extends RuntimeException {

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
