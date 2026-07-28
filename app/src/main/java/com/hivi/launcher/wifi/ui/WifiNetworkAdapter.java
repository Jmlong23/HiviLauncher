package com.hivi.launcher.wifi.ui;

import android.content.Context;
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
        holder.binding.wifiNetworkName.setText(createNetworkLabel(network));
        holder.binding.wifiNetworkCheck.setVisibility(network.isConnected()
                ? View.VISIBLE : View.INVISIBLE);
        holder.binding.wifiNetworkProgress.setVisibility(network.isConnecting()
                ? View.VISIBLE : View.GONE);
        holder.binding.wifiNetworkSignal.setVisibility(network.isConnecting()
                ? View.INVISIBLE : View.VISIBLE);
        holder.binding.wifiNetworkItem.setEnabled(!network.isConnecting());
        holder.binding.wifiNetworkItem.setContentDescription(network.getSsid());
        holder.binding.wifiNetworkItem.setOnClickListener(view -> {
            if (mClickListener != null && !network.isConnecting()) {
                mClickListener.onWifiNetworkClicked(network);
            }
        });
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

    static final class WifiNetworkViewHolder extends RecyclerView.ViewHolder {
        final ItemWifiNetworkBinding binding;

        WifiNetworkViewHolder(ItemWifiNetworkBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
