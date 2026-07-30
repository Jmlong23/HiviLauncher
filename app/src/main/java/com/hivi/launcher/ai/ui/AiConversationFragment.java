package com.hivi.launcher.ai.ui;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hivi.launcher.R;
import com.hivi.launcher.ai.presenter.AiConversationPresenter;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.customview.ParticleVisualizerView;
import com.hivi.launcher.databinding.FragmentAiConversationBinding;
import com.hivi.launcher.main.ui.MainActivity;

public final class AiConversationFragment extends BaseFragment<AiConversationPresenter>
        implements AiConversationView {
    private static final int REQUEST_RECORD_AUDIO = 0xA1;
    private static final long TYPEWRITER_DELAY_MS = 100L;

    private final Handler mTypewriterHandler = new Handler(Looper.getMainLooper());
    private FragmentAiConversationBinding mBinding;
    private Runnable mTypewriterRunnable;
    private String mAssistantTargetText = "";
    private int mAssistantDisplayIndex;

    @Override
    protected AiConversationPresenter createPresenter() {
        Activity activity = getActivity();
        if (activity == null) {
            throw new IllegalStateException("AI conversation fragment is not attached.");
        }
        return new AiConversationPresenter(activity, this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_ai_conversation;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.ai_conversation_title;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBinding = FragmentAiConversationBinding.bind(view);
        mBinding.aiConversationBack.setOnClickListener(ignored -> requestBackNavigation());
        mBinding.aiConversationParticle.setOnClickListener(ignored -> {
            AiConversationPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.onParticleClicked();
            }
        });
        mBinding.aiConversationParticle.post(() -> {
            if (mBinding != null) {
                mBinding.aiConversationParticle.prewarm(
                        mBinding.aiConversationParticle.getWidth(),
                        mBinding.aiConversationParticle.getHeight());
            }
        });
        AiConversationPresenter presenter = getPresenter();
        if (presenter != null) {
            presenter.init();
        }
    }

    @Override
    public void onDestroyView() {
        AiConversationPresenter presenter = getPresenter();
        if (presenter != null) {
            presenter.release();
        }
        resetAssistantResponse();
        mBinding = null;
        super.onDestroyView();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) {
            return;
        }
        AiConversationPresenter presenter = getPresenter();
        if (presenter != null) {
            presenter.onRecordAudioPermissionResult(grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED);
        }
    }

    @Override
    public void renderConversationState(ParticleVisualizerView.State state, String statusText) {
        if (mBinding == null) {
            return;
        }
        mBinding.aiConversationParticle.setState(state);
        if (state != ParticleVisualizerView.State.LISTENING) {
            mBinding.aiConversationParticle.resetVolume();
        }
        mBinding.aiConversationStatus.setText(statusText);
    }

    @Override
    public void clearAssistantResponse() {
        resetAssistantResponse();
        if (mBinding != null) {
            mBinding.aiConversationHint.setText("");
            mBinding.aiConversationHintScroll.scrollTo(0, 0);
        }
    }

    @Override
    public void appendAssistantResponse(String responseText) {
        String cleanText = responseText == null ? "" : responseText
                .replace("\n", "")
                .replace("\r", "");
        if (TextUtils.isEmpty(cleanText)) {
            return;
        }
        mAssistantTargetText += cleanText;
        if (mTypewriterRunnable == null) {
            startAssistantTypewriter();
        }
    }

    @Override
    public void setParticleVolume(float volume) {
        if (mBinding != null) {
            mBinding.aiConversationParticle.setVolume(volume);
        }
    }

    @Override
    public void requestRecordAudioPermission() {
        requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
    }

    private void requestBackNavigation() {
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).onAiChatBackRequested();
        }
    }

    private void startAssistantTypewriter() {
        if (mBinding == null || mAssistantDisplayIndex >= mAssistantTargetText.length()) {
            mTypewriterRunnable = null;
            return;
        }
        mAssistantDisplayIndex++;
        mBinding.aiConversationHint.setText(
                mAssistantTargetText.substring(0, mAssistantDisplayIndex));
        scrollAssistantResponseToEnd();
        mTypewriterRunnable = this::startAssistantTypewriter;
        mTypewriterHandler.postDelayed(mTypewriterRunnable, TYPEWRITER_DELAY_MS);
    }

    private void scrollAssistantResponseToEnd() {
        if (mBinding == null) {
            return;
        }
        mBinding.aiConversationHintScroll.post(() -> {
            if (mBinding == null) {
                return;
            }
            int scrollDistance = Math.max(0, mBinding.aiConversationHint.getWidth()
                    - mBinding.aiConversationHintScroll.getWidth());
            mBinding.aiConversationHintScroll.smoothScrollTo(scrollDistance, 0);
        });
    }

    private void resetAssistantResponse() {
        if (mTypewriterRunnable != null) {
            mTypewriterHandler.removeCallbacks(mTypewriterRunnable);
            mTypewriterRunnable = null;
        }
        mAssistantTargetText = "";
        mAssistantDisplayIndex = 0;
    }
}
