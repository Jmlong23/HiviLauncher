package com.hivi.launcher.main.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hivi.launcher.R;
import com.hivi.launcher.databinding.ItemInputModeCardBinding;
import com.hivi.launcher.main.model.MainPage;

import java.util.ArrayList;
import java.util.List;

final class InputModeAdapter extends RecyclerView.Adapter<InputModeAdapter.InputModeViewHolder> {
    interface OnModeSelectedListener {
        void onModeSelected(int topLabelResId);
    }

    interface OnModeClickListener {
        void onModeClicked(MainPage page);
    }

    private static final int MODE_LINE = 0;
    private static final int MODE_MICROPHONE = 1;
    private static final int MODE_OPTICAL = 2;
    private static final int MODE_COAX = 3;
    private static final int MODE_HDMI = 4;
    private static final int MODE_BLUETOOTH = 5;
    private static final int MODE_WIFI_MUSIC = 6;

    private final Context mContext;
    private final LayoutInflater mInflater;
    private final OnModeSelectedListener mListener;
    private final OnModeClickListener mOnModeClickListener;
    private final List<InputMode> mModes = new ArrayList<>();

    private int mSelectedPosition = RecyclerView.NO_POSITION;

    InputModeAdapter(Context context, OnModeSelectedListener listener,
            OnModeClickListener onModeClickListener) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mListener = listener;
        mOnModeClickListener = onModeClickListener;
        addDefaultModes();
    }

    @NonNull
    @Override
    public InputModeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemInputModeCardBinding binding = ItemInputModeCardBinding.inflate(mInflater, parent, false);
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                dp(176), ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMargins(0, 0, dp(20), 0);
        binding.getRoot().setLayoutParams(params);
        return new InputModeViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull InputModeViewHolder holder, int position) {
        InputMode mode = mModes.get(position);
        boolean selected = position == mSelectedPosition;

        holder.binding.modeBackground.setImageResource(selected
                ? mode.selectedCardResId : mode.unselectedCardResId);
        holder.binding.modeNameText.setText(mode.title);
        holder.binding.modeSubNameText.setText(mode.subName);
        holder.binding.modeCard.setContentDescription(mode.title);
        holder.binding.modeCard.setSelected(selected);
        holder.binding.modeCard.setOnClickListener(view -> {
            com.hivi.launcher.utils.FeedbackSound.playClick();
            if (mOnModeClickListener != null) {
                mOnModeClickListener.onModeClicked(mode.page);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mModes.size();
    }

    int getSelectedModeTopLabelResId() {
        return mSelectedPosition == RecyclerView.NO_POSITION
                ? 0 : mModes.get(mSelectedPosition).topLabelResId;
    }

    int getSelectedPosition() {
        return mSelectedPosition;
    }

    MainPage getSelectedPage() {
        return mSelectedPosition == RecyclerView.NO_POSITION
                ? null : mModes.get(mSelectedPosition).page;
    }

    boolean selectMode(MainPage page) {
        if (page == null) {
            return false;
        }
        for (int position = 0; position < mModes.size(); position++) {
            if (mModes.get(position).page == page) {
                return updateSelectedPosition(position);
            }
        }
        return false;
    }

    /**
     * 兼容入口：卡片上的连接状态文字已按需求隐藏，未连接置灰改由左上角状态区表达，
     * 这里不再刷新卡片内容。
     */
    boolean updateConnectivityState(boolean bluetoothConnected, String wifiLabel) {
        return false;
    }

    private void addDefaultModes() {
        mModes.add(createMode(R.string.input_mode_line_top, R.string.input_mode_line,
                "LINE IN", R.drawable.mode_line, R.drawable.mode_line_selected, MainPage.LINE));
        mModes.add(createMode(R.string.input_mode_microphone_top,
                R.string.input_mode_microphone, "MIC",
                R.drawable.mode_microphone, R.drawable.mode_microphone_selected,
                MainPage.MICROPHONE));
        mModes.add(createMode(R.string.input_mode_optical_top, R.string.input_mode_optical,
                "OPTICAL", R.drawable.mode_optical, R.drawable.mode_optical_selected,
                MainPage.OPTICAL));
        mModes.add(createMode(R.string.input_mode_coax_top, R.string.input_mode_coax,
                "COAXIAL", R.drawable.mode_coax, R.drawable.mode_coax_selected, MainPage.COAX));
        mModes.add(createMode(R.string.input_mode_hdmi_top, R.string.input_mode_hdmi,
                "HDMI ARC", R.drawable.mode_hdmi, R.drawable.mode_hdmi_selected, MainPage.HDMI));
        mModes.add(createMode(R.string.input_mode_bluetooth_top, R.string.input_mode_bluetooth,
                "BLUETOOTH", R.drawable.mode_bluetooth, R.drawable.mode_bluetooth_selected,
                MainPage.BLUETOOTH));
        mModes.add(createMode(R.string.input_mode_wifi_music_top,
                R.string.input_mode_wifi_music, "WI-FI MUSIC",
                R.drawable.mode_wifi, R.drawable.mode_wifi_selected, MainPage.WIFI));
    }

    private InputMode createMode(int topLabelResId, int titleResId, String subName,
            int unselectedCardResId, int selectedCardResId, MainPage page) {
        return new InputMode(topLabelResId, mContext.getString(titleResId), subName,
                unselectedCardResId, selectedCardResId, page);
    }

    private boolean updateSelectedPosition(int position) {
        if (position != RecyclerView.NO_POSITION && (position < 0 || position >= mModes.size())) {
            return false;
        }
        if (position == mSelectedPosition) {
            return false;
        }
        int previousPosition = mSelectedPosition;
        mSelectedPosition = position;
        if (previousPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousPosition);
        }
        if (mSelectedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(mSelectedPosition);
        }
        if (mListener != null) {
            mListener.onModeSelected(getSelectedModeTopLabelResId());
        }
        return true;
    }

    private int dp(int value) {
        return Math.round(value * mContext.getResources().getDisplayMetrics().density);
    }

    static final class InputModeViewHolder extends RecyclerView.ViewHolder {
        final ItemInputModeCardBinding binding;

        InputModeViewHolder(ItemInputModeCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final class InputMode {
        final int topLabelResId;
        final String title;
        final String subName;
        final int unselectedCardResId;
        final int selectedCardResId;
        final MainPage page;

        InputMode(int topLabelResId, String title, String subName,
                int unselectedCardResId, int selectedCardResId, MainPage page) {
            this.topLabelResId = topLabelResId;
            this.title = title;
            this.subName = subName;
            this.unselectedCardResId = unselectedCardResId;
            this.selectedCardResId = selectedCardResId;
            this.page = page;
        }
    }
}
