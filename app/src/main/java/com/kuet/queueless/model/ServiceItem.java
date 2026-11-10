package com.kuet.queueless.model;

import com.google.firebase.firestore.DocumentId;
import com.kuet.queueless.util.Constants;

import java.util.Date;

public class ServiceItem {
    @DocumentId public String id;
    public String name;
    public String location;
    public String description;
    public String providerId;
    public String prefix;
    public String announcement;
    public String currentTokenId;   // token being served ("" when none) - acts as the "one at a time" lock
    public String nowServing;       // display number of that token
    public String dayKey;           // yyyyMMdd: token numbers restart each day
    public boolean open;            // accepting new tokens
    public double averageServiceMinutes;
    public int lastTokenNumber;
    public int waitingCount;
    public Date createdAt;

    public ServiceItem() { }

    public boolean hasCurrent() {
        return currentTokenId != null && !currentTokenId.isEmpty();
    }

    public double avg() {
        return averageServiceMinutes <= 0 ? Constants.DEFAULT_AVG_MINUTES : averageServiceMinutes;
    }
}
