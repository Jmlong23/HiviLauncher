package com.hivi.launcher.wifi.ui;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hivi.launcher.R;
import com.hivi.launcher.databinding.ItemWifiNetworkBinding;
import com.hivi.launcher.wifi.model.WifiNetwork;

import java.util.ArrayList;
import java.util.List;

public final class WifiNetworkAdapter
        extends RecyclerView.Adapter<WifiNetworkAdapter.WifiNetworkViewHolder> {
    public interface OnWifiNetworkClickListener {
        void onWifiNetworkClicked(WifiNetwork network);
    }

    private final Context mContext;
    private final LayoutInflater mInflater;
    private final OnWifiNetworkClickListener mClickListener;
    private final List<WifiNetwork> mNetworks = new ArrayList<>();

    public WifiNetworkAdapter(Context context, OnWifiNetworkClickListener clickListener) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mClickListener = clickListener;
    }

    public void submitNetworks(List<WifiNetwork> networks) {
        mNetworks.clear();
        if (networks != null) {
            mNetworks.addAll(networks);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WifiNetworkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new WifiNetworkViewHolder(ItemWifiNetworkBinding.inflate(mInflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull WifiNetworkViewHolder holder, int position) {
        WifiNetwork network = mNetworks.get(position);
        boolean isConnected = network.isConnected();
        boolean isConnecting = !isConnected && network.isConnecting();
        holder.binding.wifiNetworkName.setText(createNetworkLabel(network));
        holder.binding.wifiNetworkCheck.setVisibility(isConnected
                ? View.VISIBLE : View.GONE);
        holder.binding.wifiNetworkProgress.setVisibility(isConnecting
                ? View.VISIBLE : View.GONE);
        holder.setProgressAnimating(isConnecting);
        holder.binding.wifiNetworkSignal.setImageResource(
                getSignalDrawable(network.getSignalLevel()));
        holder.binding.wifiNetworkSignal.setVisibility(View.VISIBLE);
        holder.binding.wifiNetworkItem.setEnabled(!isConnecting);
        holder.binding.wifiNetworkItem.setContentDescription(network.getSsid());
        holder.binding.wifiNetworkItem.setOnClickListener(view -> {
            if (mClickListener != null && !isConnecting) {
                mClickListener.onWifiNetworkClicked(network);
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull WifiNetworkViewHolder holder) {
        holder.setProgressAnimating(false);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return mNetworks.size();
    }

    private String createNetworkLabel(WifiNetwork network) {
        if (network.isConnected()) {
            return mContext.getString(R.string.settings_wifi_connected_format, network.getSsid());
        }
        if (network.isConnecting()) {
            return mContext.getString(R.string.settings_wifi_connecting_format, network.getSsid());
        }
        return network.getSsid();
    }

    private int getSignalDrawable(int signalLevel) {
        switch (WifiManager.calculateSignalLevel(signalLevel, 4)) {
            case 3:
                return R.drawable.ic_wifi_4;
            case 2:
                return R.drawable.ic_wifi_3;
            case 1:
                return R.drawable.ic_wifi_2;
            default:
                return R.drawable.ic_wifi_1;
        }
    }

    static final class WifiNetworkViewHolder extends RecyclerView.ViewHolder {
        final ItemWifiNetworkBinding binding;
        private ObjectAnimator mProgressAnimator;

        WifiNetworkViewHolder(ItemWifiNetworkBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void setProgressAnimating(boolean animating) {
            if (animating) {
                if (mProgressAnimator == null) {
                    mProgressAnimator = ObjectAnimator.ofFloat(binding.wifiNetworkProgress,
                            View.ROTATION, 0f, 360f);
                    mProgressAnimator.setDuration(900L);
                    mProgressAnimator.setRepeatCount(ObjectAnimator.INFINITE);
                }
                if (!mProgressAnimator.isStarted()) {
                    mProgressAnimator.start();
                }
            } else {
                if (mProgressAnimator != null) {
                    mProgressAnimator.cancel();
                }
                binding.wifiNetworkProgress.setRotation(0f);
            }
        }
    }
}
