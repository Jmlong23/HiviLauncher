package com.hivi.launcher.main.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hivi.launcher.R;
import com.hivi.launcher.databinding.ItemInputModeCardBinding;

import java.util.ArrayList;
import java.util.List;

final class InputModeAdapter extends RecyclerView.Adapter<InputModeAdapter.InputModeViewHolder> {
    interface OnModeSelectedListener {
        void onModeSelected(int topLabelResId);
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
    private final List<InputMode> mModes = new ArrayList<>();

    private int mSelectedPosition = RecyclerView.NO_POSITION;

    InputModeAdapter(Context context, OnModeSelectedListener listener) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mListener = listener;
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
        holder.binding.modeStatusText.setText(mode.status);
        holder.binding.modeStatusText.setTextColor(mContext.getColor(mode.statusColorResId));
        holder.binding.modeCard.setContentDescription(mode.title);
        holder.binding.modeCard.setSelected(selected);
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

    boolean updateConnectivityState(boolean bluetoothConnected, String wifiLabel) {
        boolean wifiConnected = !TextUtils.isEmpty(wifiLabel)
                && !TextUtils.equals(wifiLabel, mContext.getString(R.string.main_disconnected));

        InputMode bluetoothMode = mModes.get(MODE_BLUETOOTH);
        bluetoothMode.status = mContext.getString(bluetoothConnected
                ? R.string.input_mode_status_connected
                : R.string.input_mode_status_disconnected);
        bluetoothMode.statusColorResId = bluetoothConnected
                ? R.color.status_connected : R.color.text_color;
        notifyItemChanged(MODE_BLUETOOTH);

        InputMode wifiMusicMode = mModes.get(MODE_WIFI_MUSIC);
        wifiMusicMode.status = mContext.getString(wifiConnected
                ? R.string.input_mode_status_connected
                : R.string.input_mode_status_disconnected);
        wifiMusicMode.statusColorResId = wifiConnected
                ? R.color.status_connected : R.color.text_color;
        notifyItemChanged(MODE_WIFI_MUSIC);

        // When both inputs are connected, Bluetooth takes precedence over WiFi.
        return updateSelectedPosition(bluetoothConnected ? MODE_BLUETOOTH
                : wifiConnected ? MODE_WIFI_MUSIC : RecyclerView.NO_POSITION);
    }

    private void addDefaultModes() {
        mModes.add(createMode(R.string.input_mode_line_top, R.string.input_mode_line,
                R.string.input_mode_status_no_signal,
                R.drawable.mode_line, R.drawable.mode_line_selected));
        mModes.add(createMode(R.string.input_mode_microphone_top,
                R.string.input_mode_microphone,
                R.string.input_mode_status_no_signal,
                R.drawable.mode_microphone, R.drawable.mode_microphone_selected));
        mModes.add(createMode(R.string.input_mode_optical_top, R.string.input_mode_optical,
                R.string.input_mode_status_no_signal,
                R.drawable.mode_optical, R.drawable.mode_optical_selected));
        mModes.add(createMode(R.string.input_mode_coax_top, R.string.input_mode_coax,
                R.string.input_mode_status_no_signal,
                R.drawable.mode_coax, R.drawable.mode_coax_selected));
        mModes.add(createMode(R.string.input_mode_hdmi_top, R.string.input_mode_hdmi,
                R.string.input_mode_status_no_signal,
                R.drawable.mode_hdmi, R.drawable.mode_hdmi_selected));
        mModes.add(createMode(R.string.input_mode_bluetooth_top, R.string.input_mode_bluetooth,
                R.string.input_mode_status_disconnected,
                R.drawable.mode_bluetooth, R.drawable.mode_bluetooth_selected));
        mModes.add(createMode(R.string.input_mode_wifi_music_top,
                R.string.input_mode_wifi_music, R.string.input_mode_status_disconnected,
                R.drawable.mode_wifi, R.drawable.mode_wifi_selected));
    }

    private InputMode createMode(int topLabelResId, int titleResId, int statusResId,
            int unselectedCardResId, int selectedCardResId) {
        return new InputMode(topLabelResId, mContext.getString(titleResId),
                mContext.getString(statusResId), unselectedCardResId, selectedCardResId);
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
        final int unselectedCardResId;
        final int selectedCardResId;
        String status;
        int statusColorResId = R.color.text_color;

        InputMode(int topLabelResId, String title, String status,
                int unselectedCardResId, int selectedCardResId) {
            this.topLabelResId = topLabelResId;
            this.title = title;
            this.status = status;
            this.unselectedCardResId = unselectedCardResId;
            this.selectedCardResId = selectedCardResId;
        }
    }
}
