package com.kuet.queueless.model;

import com.google.firebase.firestore.DocumentId;

import java.util.Date;

public class Token {
    @DocumentId public String id;
    public String serviceId;
    public String userId;
    public String displayNumber;   // human readable, e.g. P-024 (resets each service day)
    public String status;          // TokenStatus name
    public int tokenNumber;
    public long serviceSeconds;    // how long the service took (set on completion)
    public Date createdAt;
    public Date calledAt;
    public Date completedAt;

    public Token() { }

    public TokenStatus statusEnum() {
        return TokenStatus.from(status);
    }

    /** Pending server timestamps are null locally: treat them as "newest". */
    public long createdMillis() {
        return createdAt == null ? Long.MAX_VALUE : createdAt.getTime();
    }

    public boolean isBefore(Token o) {
        long a = createdMillis();
        long b = o.createdMillis();
        if (a != b) return a < b;
        return tokenNumber < o.tokenNumber;
    }
}
