package com.kuet.queueless.model;

/** Legal token states and the transitions between them. */
public enum TokenStatus {
    WAITING, CALLED, COMPLETED, SKIPPED, CANCELLED;

    public boolean canTransitionTo(TokenStatus next) {
        switch (this) {
            case WAITING:
                return next == CALLED || next == SKIPPED || next == CANCELLED;
            case CALLED:
                return next == COMPLETED || next == SKIPPED;
            default:
                return false; // COMPLETED, SKIPPED, CANCELLED are final
        }
    }

    public boolean isActive() {
        return this == WAITING || this == CALLED;
    }

    public static TokenStatus from(String s) {
        try {
            return valueOf(s);
        } catch (Exception e) {
            return CANCELLED;
        }
    }
}
