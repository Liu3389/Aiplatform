package com.ai.gateway.exception;

public class OAuthException extends RuntimeException {
    private final int status;

    public OAuthException(int status, String message) {
        super(message);
        this.status = status;
    }

    public OAuthException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
