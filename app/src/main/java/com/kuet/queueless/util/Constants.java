package com.kuet.queueless.util;

public final class Constants {
    private Constants() {}

    public static final String COL_USERS = "users";
    public static final String COL_SERVICES = "services";
    public static final String COL_TOKENS = "tokens";
    public static final String COL_EVENTS = "queueEvents";

    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_OPERATOR = "OPERATOR";

    /** Light client-side gate for operator sign-up (course project). Change it before your demo. */
    public static final String OPERATOR_CODE = "KUET-OP-2026";

    public static final String EXTRA_SERVICE_ID = "serviceId";
    public static final String EXTRA_ROLE = "role";

    public static final String CHANNEL_ID = "queue_updates";
    public static final String QR_PREFIX = "QLK:";

    /** Notify the student when this many (or fewer) people are ahead. */
    public static final int NEAR_THRESHOLD = 2;
    public static final double DEFAULT_AVG_MINUTES = 5.0;
}
