package com.hivi.launcher.utils.network;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

/**
 * Request-body wrapper that reports multipart upload progress from OkHttp's I/O thread.
 */
public final class ProgressRequestBody extends RequestBody {
    public interface ProgressCallback {
        void onProgress(int percent);
    }

    private final RequestBody mDelegate;
    private final ProgressCallback mProgressCallback;

    public ProgressRequestBody(RequestBody delegate, ProgressCallback progressCallback) {
        mDelegate = delegate;
        mProgressCallback = progressCallback;
    }

    @Override
    public MediaType contentType() {
        return mDelegate.contentType();
    }

    @Override
    public long contentLength() throws IOException {
        return mDelegate.contentLength();
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        final long totalBytes = contentLength();
        final long[] writtenBytes = {0L};
        final int[] lastPercent = {-1};
        Sink progressSink = new ForwardingSink(sink) {
            @Override
            public void write(Buffer source, long byteCount) throws IOException {
                super.write(source, byteCount);
                writtenBytes[0] += byteCount;
                if (totalBytes <= 0L || mProgressCallback == null) {
                    return;
                }
                int percent = (int) (writtenBytes[0] * 100L / totalBytes);
                if (percent != lastPercent[0]) {
                    lastPercent[0] = percent;
                    mProgressCallback.onProgress(percent);
                }
            }
        };
        BufferedSink bufferedSink = Okio.buffer(progressSink);
        mDelegate.writeTo(bufferedSink);
        bufferedSink.flush();
        if (totalBytes > 0L && mProgressCallback != null && lastPercent[0] < 100) {
            mProgressCallback.onProgress(100);
        }
    }
}
