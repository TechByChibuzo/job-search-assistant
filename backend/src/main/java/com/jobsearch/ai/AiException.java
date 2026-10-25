package com.jobsearch.ai;

/** Claude couldn't do what was asked; the message is written for the owner. */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
