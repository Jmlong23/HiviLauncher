package com.hivi.launcher.utils.network;

public interface NetworkCallback<T> {
    void onSuccess(T result);

    void onFailure(Throwable throwable);
}
