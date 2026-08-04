package com.hivi.launcher.utils.network;

import android.content.Context;
import android.text.TextUtils;
import com.hivi.launcher.utils.log.AppLog;

import com.hivi.launcher.utils.Constants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Cache;
import okhttp3.Headers;
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
    private static final long VERSION_RESPONSE_LOG_LIMIT_BYTES = 64L * 1024L;
    private static final int LOG_CHUNK_SIZE = 3_000;

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
                        AppLog.e(TAG, "Network request failed", throwable);
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
                .addInterceptor(new VersionDetailsLoggingInterceptor())
                .build();
        return new Retrofit.Builder()
                .baseUrl(Constants.TEST_BASE_URL)
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

    /**
     * Logs the version endpoint request and response for test verification.
     * Authorization-related values and signed URL query parameters are redacted.
     */
    private static final class VersionDetailsLoggingInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws java.io.IOException {
            Request request = chain.request();
            if (!request.url().encodedPath().endsWith("/version/details")) {
                return chain.proceed(request);
            }

            long startNanos = System.nanoTime();
            AppLog.i(TAG, "Version API request: method=" + request.method()
                    + ", url=" + request.url());
            try {
                Response response = chain.proceed(request);
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                if (response.body() != null) {
                    String body = response.peekBody(VERSION_RESPONSE_LOG_LIMIT_BYTES).string();
                    logLongMessage("Version API response body:\n"
                            + sanitizeResponseBodyForLog(body));
                }
                return response;
            } catch (java.io.IOException exception) {
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                AppLog.e(TAG, "Version API request failed: url=" + request.url()
                        + ", durationMs=" + elapsedMs, exception);
                throw exception;
            }
        }
    }

    private static void logLongMessage(String message) {
        if (TextUtils.isEmpty(message)) {
            AppLog.i(TAG, "");
            return;
        }
        for (int start = 0; start < message.length(); start += LOG_CHUNK_SIZE) {
            int end = Math.min(message.length(), start + LOG_CHUNK_SIZE);
            AppLog.i(TAG, message.substring(start, end));
        }
    }

    private static String formatHeadersForLog(Headers headers) {
        if (headers == null || headers.size() == 0) {
            return "(none)";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < headers.size(); index++) {
            String name = headers.name(index);
            builder.append(name).append(": ")
                    .append(isSensitiveHeader(name) ? "[redacted]" : headers.value(index))
                    .append('\n');
        }
        return builder.toString();
    }

    private static boolean isSensitiveHeader(String name) {
        return "Authorization".equalsIgnoreCase(name)
                || "Cookie".equalsIgnoreCase(name)
                || "Set-Cookie".equalsIgnoreCase(name);
    }

    private static String sanitizeResponseBodyForLog(String responseBody) {
        try {
            Object json = new org.json.JSONTokener(responseBody).nextValue();
            redactSensitiveJsonValues(json);
            return String.valueOf(json);
        } catch (Exception ignored) {
            return responseBody;
        }
    }

    private static void redactSensitiveJsonValues(Object value) throws Exception {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object child = object.opt(key);
                if (isSensitiveJsonField(key)) {
                    object.put(key, "[redacted]");
                } else if ("updateUrl".equalsIgnoreCase(key) && child instanceof String) {
                    object.put(key, redactUrlQuery((String) child));
                } else {
                    redactSensitiveJsonValues(child);
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                redactSensitiveJsonValues(array.opt(index));
            }
        }
    }

    private static boolean isSensitiveJsonField(String key) {
        return key != null && (key.equalsIgnoreCase("token")
                || key.equalsIgnoreCase("accessToken")
                || key.equalsIgnoreCase("refreshToken")
                || key.equalsIgnoreCase("password")
                || key.equalsIgnoreCase("secret")
                || key.equalsIgnoreCase("authorization"));
    }

    private static String redactUrlQuery(String url) {
        int queryIndex = url.indexOf('?');
        return queryIndex < 0 ? url : url.substring(0, queryIndex) + "?[redacted]";
    }
}
