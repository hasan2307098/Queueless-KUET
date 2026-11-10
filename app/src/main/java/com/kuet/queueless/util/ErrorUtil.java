package com.kuet.queueless.util;

import com.google.firebase.firestore.FirebaseFirestoreException;

public final class ErrorUtil {
    private ErrorUtil() {}

    public static String message(Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException f = (FirebaseFirestoreException) e;
            switch (f.getCode()) {
                case PERMISSION_DENIED:
                    return "You don't have permission to do that.";
                case UNAVAILABLE:
                    return "No connection. Check your internet and try again.";
                default:
                    break;
            }
        }
        String m = e.getMessage();
        return (m == null || m.isEmpty()) ? "Something went wrong." : m;
    }
}
