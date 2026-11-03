package com.hivi.launcher.systemapps.model;

import org.junit.Test;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PresetAppInstallerTaskTest {
    @Test
    public void downloadCanBePausedBeforeNetworkStarts() {
        PresetAppInstaller.DownloadTask task = new PresetAppInstaller.DownloadTask();
        assertTrue(task.pause());
        assertTrue(task.isPaused());
    }

    @Test(expected = IOException.class)
    public void pausedTaskCannotBeginInstallation() throws IOException {
        PresetAppInstaller.DownloadTask task = new PresetAppInstaller.DownloadTask();
        task.pause();
        task.beginInstallation();
    }

    @Test
    public void installingTaskCannotBeInterruptedByPause() throws IOException {
        PresetAppInstaller.DownloadTask task = new PresetAppInstaller.DownloadTask();
        task.beginInstallation();

        assertFalse(task.pause());
        assertTrue(task.isInstalling());
        assertFalse(task.isPaused());
    }

    @Test
    public void finishedTaskCannotBePaused() {
        PresetAppInstaller.DownloadTask task = new PresetAppInstaller.DownloadTask();
        task.finish();
        assertFalse(task.pause());
    }

    @Test
    public void pauseCancelsTheActiveNetworkCall() throws IOException {
        PresetAppInstaller.DownloadTask task = new PresetAppInstaller.DownloadTask();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    assertTrue(task.pause());
                    assertTrue(chain.call().isCanceled());
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create(null, new byte[0]))
                            .build();
                }).build();

        try (Response ignored = task.execute(client,
                new Request.Builder().url("https://example.com/app.apk").build())) {
            fail("A paused download must cancel its active request.");
        } catch (IOException expected) {
            assertTrue(task.isPaused());
        }
    }

    @Test
    public void pausedTaskNeverStartsANetworkCall() throws IOException {
        PresetAppInstaller.DownloadTask task = new PresetAppInstaller.DownloadTask();
        task.pause();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    fail("A paused download must not start a network request.");
                    throw new IOException("Unexpected request");
                }).build();

        try (Response ignored = task.execute(client,
                new Request.Builder().url("https://example.com/app.apk").build())) {
            fail("A paused download must reject the request.");
        } catch (IOException expected) {
            assertTrue(task.isPaused());
        }
    }
}
