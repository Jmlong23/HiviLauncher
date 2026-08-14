package com.hivi.launcher.ai.wakeup;

/**
 * 唤醒流程回调，回调均在主线程执行。
 */
public interface FlyAiIVWListener {
    /** 引擎授权成功、开始监听唤醒词。 */
    default void onInitSdkSuccess() {
    }

    /**
     * 唤醒检测到的最早回调，可用于提前发送耗时/需排队的指令。
     *
     * @return 需要延迟后续流程（播放提示音、开麦）的毫秒数
     */
    default long onFlyAIWakeupDetected(FlyAiIVW.WakeupInfo wakeupInfo) {
        return 0L;
    }

    /**
     * 播放唤醒提示音之前的回调。
     *
     * @return true 继续播放唤醒提示音，false 取消播放
     */
    default boolean onFlyAIPreWakeStop(FlyAiIVW.WakeupInfo wakeupInfo) {
        return true;
    }

    /** 唤醒提示音播放结束（或被取消/超时），此时可以开麦。 */
    void onFlyAIResponse(FlyAiIVW.WakeupInfo wakeupInfo);
}
