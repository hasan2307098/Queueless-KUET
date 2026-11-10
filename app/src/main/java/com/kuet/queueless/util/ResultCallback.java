package com.kuet.queueless.util;

public interface ResultCallback<T> {
    void onSuccess(T value);
    void onError(String message);
}
