package com.ljm.audiotoollib.upnpserver.service;

import java.util.Arrays;
import java.util.Objects;

/** Coordinates applied wallpaper state and cloud-confirmed subscription snapshots. */
public final class WallpaperController {
    public static final String UNKNOWN = "unknown";
    public static final String PRESET = "preset";
    public static final String CUSTOM = "custom";
    public static final String CAROUSEL = "carousel";
    public static final String FOLLOW_ROLE = "follow_role";
    public static final String ART_CLOCK = "art_clock";

    public static final class State {
        private final String source;
        private final int[] ids;
        private final int clockId;
        private final int clockStyle;

        public State(String source, int... ids) {
            this(source, ids, 0, 0);
        }

        public static State artClock(int clockId, int clockStyle) {
            return new State(ART_CLOCK, new int[0], clockId, clockStyle);
        }

        private State(String source, int[] ids, int clockId, int clockStyle) {
            if (!PRESET.equals(source) && !CUSTOM.equals(source) && !CAROUSEL.equals(source)
                    && !FOLLOW_ROLE.equals(source) && !ART_CLOCK.equals(source) && !UNKNOWN.equals(source)) {
                throw new IllegalArgumentException("Invalid wallpaper source");
            }
            if (ids == null || ((PRESET.equals(source) || CUSTOM.equals(source)) && ids.length != 1)
                    || (CAROUSEL.equals(source) && ids.length == 0)
                    || ((!PRESET.equals(source) && !CUSTOM.equals(source) && !CAROUSEL.equals(source))
                    && ids.length != 0)) {
                throw new IllegalArgumentException("Invalid wallpaper resource list");
            }
            for (int id : ids) {
                if (id <= 0) throw new IllegalArgumentException("Wallpaper resource IDs must be positive");
            }
            if (ART_CLOCK.equals(source)
                    ? clockId <= 0 || clockStyle < 1 || clockStyle > 3
                    : clockId != 0 || clockStyle != 0) {
                throw new IllegalArgumentException("Invalid clock resource or style");
            }
            this.source = source;
            this.ids = ids.clone();
            this.clockId = clockId;
            this.clockStyle = clockStyle;
        }

        public String getSource() { return source; }
        public int getId() { return ids.length == 0 ? 0 : ids[0]; }
        public String getIdsJson() { return Arrays.toString(ids); }
        public int getClockId() { return clockId; }
        public int getClockStyle() { return clockStyle; }
        public int getUploadType() { return ART_CLOCK.equals(source) ? 7 : 6; }
        public int getUploadId() { return ART_CLOCK.equals(source) ? clockId : getId(); }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof State)) return false;
            State state = (State) other;
            return source.equals(state.source) && Arrays.equals(ids, state.ids)
                    && clockId == state.clockId && clockStyle == state.clockStyle;
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, Arrays.hashCode(ids), clockId, clockStyle);
        }
    }

    public interface OnWallpaperChangedListener {
        void onWallpaperChanged(State state);
    }

    private static final WallpaperController INSTANCE = new WallpaperController();
    private State confirmedState = new State(UNKNOWN);
    private State pendingState;
    private long version;
    private boolean uploadStarted;
    private OnWallpaperChangedListener listener;

    public static WallpaperController getInstance() { return INSTANCE; }

    public synchronized State getConfirmedState() { return confirmedState; }

    public synchronized long stageLocalWallpaper(State state) {
        if (!PRESET.equals(state.getSource()) && !ART_CLOCK.equals(state.getSource())) {
            throw new IllegalArgumentException("Local selection must be a preset or art clock");
        }
        pendingState = state;
        uploadStarted = false;
        return ++version;
    }

    /** A replacement request invalidates old uploads before asynchronous loading finishes. */
    public synchronized void prepareWallpaper(State state) {
        if (pendingState != null && !pendingState.equals(state)) {
            pendingState = null;
            uploadStarted = false;
            version++;
        }
    }

    /** Returns an upload token once, only after the pending local selection is applied. */
    public synchronized long reportAppliedWallpaper(State state) {
        Objects.requireNonNull(state);
        prepareWallpaper(state);
        if (pendingState != null) {
            if (uploadStarted) return 0;
            uploadStarted = true;
            return version;
        }
        publish(state);
        return 0;
    }

    public synchronized boolean isPendingWallpaperUpload(long token) {
        return pendingState != null && uploadStarted && token == version;
    }

    public synchronized void suspendPendingWallpaperUpload() {
        if (pendingState != null) {
            uploadStarted = false;
            version++;
        }
    }

    public synchronized boolean confirmWallpaperUpload(long token) {
        if (!isPendingWallpaperUpload(token)) return false;
        State state = pendingState;
        pendingState = null;
        uploadStarted = false;
        publish(state);
        return true;
    }

    private void publish(State state) {
        if (confirmedState.equals(state)) return;
        confirmedState = state;
        if (listener != null) listener.onWallpaperChanged(state);
    }

    public synchronized void setOnWallpaperChangedListener(OnWallpaperChangedListener next) {
        listener = next;
    }

    public synchronized void clearOnWallpaperChangedListener(OnWallpaperChangedListener previous) {
        if (listener == previous) listener = null;
    }
}
