package com.hivi.launcher.account.model;

/**
 * UI-neutral state emitted by the AI account authorization flow.
 */
public final class AuthorizationUiState {
    public enum Phase {
        LOADING,
        WAITING_FOR_SCAN,
        SCANNED,
        AUTHORIZED,
        RETRYING,
        CANCELING,
        CANCEL_FAILED
    }

    public enum RetryReason {
        NETWORK,
        EXPIRED,
        CANCELED
    }

    private final Phase phase;
    private final String qrPayload;
    private final RetryReason retryReason;

    private AuthorizationUiState(Phase phase, String qrPayload, RetryReason retryReason) {
        this.phase = phase;
        this.qrPayload = qrPayload;
        this.retryReason = retryReason;
    }

    public static AuthorizationUiState loading() {
        return new AuthorizationUiState(Phase.LOADING, "", null);
    }

    public static AuthorizationUiState waitingForScan(String qrPayload) {
        return new AuthorizationUiState(Phase.WAITING_FOR_SCAN, qrPayload, null);
    }

    public static AuthorizationUiState scanned() {
        return new AuthorizationUiState(Phase.SCANNED, "", null);
    }

    public static AuthorizationUiState authorized() {
        return new AuthorizationUiState(Phase.AUTHORIZED, "", null);
    }

    public static AuthorizationUiState retrying(RetryReason reason) {
        return new AuthorizationUiState(Phase.RETRYING, "", reason);
    }

    public static AuthorizationUiState canceling() {
        return new AuthorizationUiState(Phase.CANCELING, "", null);
    }

    public static AuthorizationUiState cancelFailed() {
        return new AuthorizationUiState(Phase.CANCEL_FAILED, "", null);
    }

    public Phase getPhase() {
        return phase;
    }

    public String getQrPayload() {
        return qrPayload;
    }

    public RetryReason getRetryReason() {
        return retryReason;
    }
}
