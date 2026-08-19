package com.hivi.launcher.ai.iot;

import android.content.Context;
import android.text.TextUtils;

import com.hivi.launcher.audio.AudioRouteController;
import com.hivi.launcher.music.model.UpnpPlaybackManager;
import com.hivi.launcher.utils.log.AppLog;
import com.hivi.launcher.wifi.model.MediaSessionPlaybackManager;

import org.json.JSONObject;

/**
 * AI IoT 指令执行器（逻辑自 HiviAudio AIPresenter.handleAiIotCmd 迁移）。
 *
 * <p>服务端在 tts sentence_start 的 text 中下发 JSON 指令，识别规则与 HiviAudio 一致：
 * 文本包含 iotCode / iotVolume / iotLight 之一即视为 IoT 指令。指令直接在本机执行，
 * 不进入 AI 对话页面；音量类指令通过 WebSocket detect 文本让服务器合成提示音 TTS。</p>
 *
 * <ul>
 *   <li>iotVolume "50%" / 6007(音量+) / 6008(音量-)：串口功放音量；静音先播提示音再执行；</li>
 *   <li>6001/6002：上一首/下一首；6003/6004：播放/暂停 —— 优先当前音乐 App 的
 *       MediaSession，其次本机 UPnP 渲染器；</li>
 *   <li>iotLight / 6009 / 6010：亮度，Launcher 暂无亮度控制基础设施，仅记录日志。</li>
 * </ul>
 */
public final class AiIotCommandExecutor {
    private static final String TAG = "AiIotCommandExecutor";
    private static final int VOLUME_MIN = 0;
    private static final int VOLUME_MAX = 100;
    private static final int VOLUME_STEP = 5;

    private AiIotCommandExecutor() {
    }

    /** 与 HiviAudio isIotCommandText 一致：包含任一指令字段即视为 IoT 指令。 */
    public static boolean isIotCommandText(String text) {
        return !TextUtils.isEmpty(text)
                && (text.contains("iotCode") || text.contains("iotVolume")
                        || text.contains("iotLight"));
    }

    public static Result execute(Context context, String text) {
        try {
            JSONObject object = new JSONObject(text.trim());
            String iotVolume = object.optString("iotVolume");
            String iotLight = object.optString("iotLight");
            String iotCode = object.optString("iotCode").trim();

            if (!TextUtils.isEmpty(iotVolume)) {
                return executeVolumeValue(iotVolume);
            }
            if (!TextUtils.isEmpty(iotLight)) {
                AppLog.w(TAG, "brightness IoT command is not supported on this device");
                return Result.handledWithoutPrompt();
            }
            switch (iotCode) {
                case "6001":
                    return executeMusicTransport(context, MusicCommand.PREVIOUS);
                case "6002":
                    return executeMusicTransport(context, MusicCommand.NEXT);
                case "6003":
                    return executeMusicTransport(context, MusicCommand.PLAY);
                case "6004":
                    return executeMusicTransport(context, MusicCommand.PAUSE);
                case "6007":
                    return executeVolumeValue(
                            String.valueOf(currentVolume() + VOLUME_STEP));
                case "6008":
                    return executeVolumeValue(
                            String.valueOf(currentVolume() - VOLUME_STEP));
                case "6009":
                case "6010":
                    AppLog.w(TAG, "brightness IoT command is not supported on this device");
                    return Result.handledWithoutPrompt();
                default:
                    AppLog.w(TAG, "unknown IoT command ignored: " + iotCode);
                    return Result.handledWithoutPrompt();
            }
        } catch (Exception e) {
            AppLog.w(TAG, "parse IoT command failed: " + e.getMessage());
            return Result.handledWithoutPrompt();
        }
    }

    /** 音量绝对值（可带 %）；静音延后到提示音播完，其余立即生效。 */
    private static Result executeVolumeValue(String value) {
        String volumeText = value.trim();
        if (volumeText.endsWith("%")) {
            volumeText = volumeText.substring(0, volumeText.length() - 1).trim();
        }
        int volume;
        try {
            volume = Math.round(Float.parseFloat(volumeText));
        } catch (NumberFormatException e) {
            AppLog.w(TAG, "invalid iotVolume: " + value);
            return Result.handledWithoutPrompt();
        }
        if (volume < VOLUME_MIN || volume > VOLUME_MAX) {
            AppLog.w(TAG, "iotVolume out of range: " + volume);
            return Result.handledWithoutPrompt();
        }

        if (volume <= VOLUME_MIN) {
            // 与 HiviAudio 一致：静音先播报"已静音"，提示音结束再真正静音，避免听不到。
            return Result.withDeferredVolume(VOLUME_MIN);
        }
        applyVolume(volume);
        if (volume >= VOLUME_MAX) {
            return Result.withPrompt("音量已是最大。");
        }
        return Result.withPrompt("声音已调到" + volume);
    }

    private static Result executeMusicTransport(Context context, MusicCommand command) {
        MediaSessionPlaybackManager mediaSessions = MediaSessionPlaybackManager.getInstance();
        if (context != null) {
            mediaSessions.start(context);
        }
        if (mediaSessions.getCurrentState().hasSession()) {
            switch (command) {
                case PREVIOUS:
                    mediaSessions.previous();
                    break;
                case NEXT:
                    mediaSessions.next();
                    break;
                case PLAY:
                case PAUSE:
                    mediaSessions.playOrPause();
                    break;
            }
        } else {
            UpnpPlaybackManager upnp = UpnpPlaybackManager.getInstance();
            switch (command) {
                case PREVIOUS:
                    upnp.previous();
                    break;
                case NEXT:
                    upnp.next();
                    break;
                case PLAY:
                case PAUSE:
                    upnp.playOrPause();
                    break;
            }
        }
        return Result.handledWithoutPrompt();
    }

    public static void applyVolume(int volumePercent) {
        AudioRouteController.getInstance().setAmplifierVolume(volumePercent);
    }

    private static int currentVolume() {
        return AudioRouteController.getInstance().getAmplifierVolumePercent();
    }

    private enum MusicCommand {
        PREVIOUS,
        NEXT,
        PLAY,
        PAUSE
    }

    /** handled=false 表示不是可处理的 IoT 指令，按普通对话继续。 */
    public static final class Result {
        public final boolean handled;
        /** 提示语文本，非空时经 WS detect 下发由服务器合成 TTS 播报。 */
        public final String promptText;
        /** 提示音播完后才执行的音量（静音场景）。 */
        public final Integer deferredVolume;

        private Result(boolean handled, String promptText, Integer deferredVolume) {
            this.handled = handled;
            this.promptText = promptText == null ? "" : promptText;
            this.deferredVolume = deferredVolume;
        }

        static Result handledWithoutPrompt() {
            return new Result(true, "", null);
        }

        static Result withPrompt(String promptText) {
            return new Result(true, promptText, null);
        }

        static Result withDeferredVolume(int volume) {
            return new Result(true, "已静音", volume);
        }
    }
}
