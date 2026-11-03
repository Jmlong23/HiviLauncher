package com.hivi.launcher.systemapps.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

final class AppDownloadSource {
    interface RequestExecutor {
        Response execute(Request request) throws IOException;
    }

    private AppDownloadSource() {
    }

    static String versionUrl(String baseUrl, String serverType) {
        HttpUrl url = HttpUrl.parse(baseUrl);
        if (url == null) {
            throw new IllegalArgumentException("Invalid app server URL.");
        }
        return url.newBuilder().addPathSegment("version").addPathSegment("details")
                .addQueryParameter("type", serverType).build().toString();
    }

    static String resolve(String source, RequestExecutor executor) throws IOException {
        HttpUrl url = HttpUrl.parse(source);
        if (url == null) {
            throw new IOException("Invalid app download URL.");
        }
        if (!url.encodedPath().endsWith("/version/details")) {
            return url.toString();
        }
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = executor.execute(request)) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("App version request failed. HTTP " + response.code());
            }
            JSONObject data = new JSONObject(response.body().string()).optJSONObject("data");
            String downloadUrl = data == null ? "" : data.optString("updateUrl", "").trim();
            HttpUrl apkUrl = HttpUrl.parse(downloadUrl);
            if (apkUrl == null) {
                throw new IOException("App version response has no valid updateUrl.");
            }
            return apkUrl.toString();
        } catch (JSONException exception) {
            throw new IOException("Malformed app version response.", exception);
        }
    }
}
