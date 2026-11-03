package com.hivi.launcher.systemapps.model;

import org.junit.Test;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AppDownloadSourceTest {
    @Test
    public void versionUrlUsesTheSelectedServerAndAppType() {
        assertEquals("https://network.hivi.com/api/version/details?type=QQ-MUSIC",
                AppDownloadSource.versionUrl("https://network.hivi.com/api/", "QQ-MUSIC"));
        assertEquals("http://8.129.106.82/api/version/details?type=WE-SING",
                AppDownloadSource.versionUrl("http://8.129.106.82/api/", "WE-SING"));
    }

    @Test
    public void directApkUrlDoesNotRequestVersionMetadata() throws IOException {
        String url = "https://cdn.example.com/music.apk?token=abc";
        assertEquals(url, AppDownloadSource.resolve(url, request -> {
            fail("Direct APK links must not be fetched as JSON.");
            return null;
        }));
    }

    @Test
    public void versionResponseResolvesThePublishedApkUrl() throws IOException {
        String source = "https://network.hivi.com/api/version/details?type=QQ-MUSIC";
        assertEquals("https://cdn.example.com/qq.apk", AppDownloadSource.resolve(source, request -> {
            assertEquals(source, request.url().toString());
            assertEquals("GET", request.method());
            return response(request, 200,
                    "{\"data\":{\"updateUrl\":\"https://cdn.example.com/qq.apk\"}}");
        }));
    }

    @Test
    public void versionIsQueriedAgainWhenTheBackendPublishesANewPackage() throws IOException {
        String source = "https://network.hivi.com/api/version/details?type=QQ-MUSIC";
        for (int version = 1; version <= 2; version++) {
            String apkUrl = "https://cdn.example.com/qq-" + version + ".apk";
            assertEquals(apkUrl, AppDownloadSource.resolve(source, request -> response(request,
                    200, "{\"data\":{\"updateUrl\":\"" + apkUrl + "\"}}")));
        }
    }

    @Test(expected = IOException.class)
    public void httpFailureIsReportedAsDownloadFailure() throws IOException {
        AppDownloadSource.resolve("https://example.com/api/version/details", request ->
                response(request, 503, "{}"));
    }

    @Test(expected = IOException.class)
    public void malformedJsonIsRejected() throws IOException {
        resolveBody("not json");
    }

    @Test(expected = IOException.class)
    public void missingDataIsRejected() throws IOException {
        resolveBody("{}");
    }

    @Test(expected = IOException.class)
    public void missingUpdateUrlIsRejected() throws IOException {
        resolveBody("{\"data\":{}}");
    }

    @Test(expected = IOException.class)
    public void invalidApkProtocolIsRejected() throws IOException {
        resolveBody("{\"data\":{\"updateUrl\":\"file:///data/local/tmp/app.apk\"}}");
    }

    @Test(expected = IOException.class)
    public void pausingVersionResolutionPropagatesCancellation() throws IOException {
        AppDownloadSource.resolve("https://example.com/api/version/details", request -> {
            throw new IOException("Canceled");
        });
    }

    private void resolveBody(String body) throws IOException {
        AppDownloadSource.resolve("https://example.com/api/version/details", request ->
                response(request, 200, body));
    }

    private Response response(Request request, int status, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(status).message("test")
                .body(ResponseBody.create(MediaType.get("application/json"), body)).build();
    }
}
