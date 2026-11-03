package com.hivi.launcher.systemapps.model;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class DownloadRangeTest {
    @Test
    public void validResumeRangeReturnsTotalPackageSize() throws IOException {
        assertEquals(1000L, DownloadRange.validate("bytes 400-999/1000", 400L, 600L));
    }

    @Test
    public void chunkedPartialResponseCanOmitContentLength() throws IOException {
        assertEquals(1000L, DownloadRange.validate("bytes 400-999/1000", 400L, -1L));
    }

    @Test(expected = IOException.class)
    public void missingRangeCannotBeAppendedToPartialApk() throws IOException {
        DownloadRange.validate(null, 400L, 600L);
    }

    @Test(expected = IOException.class)
    public void wrongOffsetCannotCorruptPartialApk() throws IOException {
        DownloadRange.validate("bytes 0-999/1000", 400L, 1000L);
    }

    @Test(expected = IOException.class)
    public void inconsistentBodyLengthIsRejected() throws IOException {
        DownloadRange.validate("bytes 400-999/1000", 400L, 500L);
    }

    @Test(expected = IOException.class)
    public void rangeBeyondPackageSizeIsRejected() throws IOException {
        DownloadRange.validate("bytes 400-1000/1000", 400L, 601L);
    }

    @Test(expected = IOException.class)
    public void descendingRangeIsRejected() throws IOException {
        DownloadRange.validate("bytes 400-399/1000", 400L, 0L);
    }

    @Test(expected = IOException.class)
    public void overflowedRangeIsRejected() throws IOException {
        DownloadRange.validate("bytes 0-9999999999999999999999/1000", 0L, -1L);
    }
}
