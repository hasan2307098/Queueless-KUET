package com.kuet.queueless.util;

import android.app.Activity;
import android.content.Intent;

import com.kuet.queueless.ui.LoginActivity;
import com.kuet.queueless.ui.OperatorActivity;
import com.kuet.queueless.ui.StudentHomeActivity;

public final class Navigator {
    private Navigator() {}

    public static void goHome(Activity a, String role) {
        Class<?> target = Constants.ROLE_OPERATOR.equals(role)
                ? OperatorActivity.class : StudentHomeActivity.class;
        Intent i = new Intent(a, target);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finish();
    }

    public static void toLogin(Activity a) {
        Intent i = new Intent(a, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finish();
    }
}
