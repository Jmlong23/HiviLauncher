package com.hivi.launcher.utils.network;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.hivi.launcher.utils.Constants;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public final class NetworkManager {
    private static final String TAG = "NetworkManager";
    private static final long CACHE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final long TIMEOUT_SECONDS = 15L;

    private static volatile Context sApplicationContext;
    private static volatile ApiService sApiService;

    private NetworkManager() {
    }

    public static void initialize(Context context) {
        sApplicationContext = context.getApplicationContext();
    }

    public static ApiService getApiService() {
        if (sApiService == null) {
            synchronized (NetworkManager.class) {
                if (sApiService == null) {
                    sApiService = createRetrofit().create(ApiService.class);
                }
            }
        }
        return sApiService;
    }

    public static <T> Disposable execute(Observable<T> request, final NetworkCallback<T> callback) {
        return request.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<T>() {
                    @Override
                    public void accept(T result) {
                        callback.onSuccess(result);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        Log.e(TAG, "Network request failed", throwable);
                        callback.onFailure(throwable);
                    }
                });
    }

    private static Retrofit createRetrofit() {
        Context context = getApplicationContext();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .cache(new Cache(context.getCacheDir(), CACHE_SIZE_BYTES))
                .addInterceptor(new AuthorizationInterceptor(context))
                .build();
        return new Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .client(client)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();
    }

    private static Context getApplicationContext() {
        Context context = sApplicationContext;
        if (context == null) {
            throw new IllegalStateException("NetworkManager must be initialized before use.");
        }
        return context;
    }

    private static final class AuthorizationInterceptor implements Interceptor {
        private final Context mContext;

        private AuthorizationInterceptor(Context context) {
            mContext = context;
        }

        @Override
        public Response intercept(Chain chain) throws java.io.IOException {
            Request.Builder builder = chain.request().newBuilder();
            String token = AuthorizationStore.getToken(mContext);
            if (!TextUtils.isEmpty(token)) {
                builder.header("Authorization", token);
            }
            return chain.proceed(builder.build());
        }
    }
}
