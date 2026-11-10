package com.kuet.queueless.util;

import android.content.Context;
import android.widget.Toast;

/** Small helpers so activities can use lambdas for callbacks. */
public final class Callbacks {
    private Callbacks() {}

    public interface OnValue<T> {
        void accept(T value);
    }

    /** Success runs the lambda, errors are shown as a Toast. */
    public static <T> ResultCallback<T> toast(final Context context, final OnValue<T> ok) {
        return new ResultCallback<T>() {
            @Override public void onSuccess(T value) { ok.accept(value); }
            @Override public void onError(String message) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        };
    }

    /** Success runs the lambda, errors are ignored. */
    public static <T> ResultCallback<T> silent(final OnValue<T> ok) {
        return new ResultCallback<T>() {
            @Override public void onSuccess(T value) { ok.accept(value); }
            @Override public void onError(String message) { }
        };
    }
}
