package com.hivi.launcher.settings.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewGroup;
import android.graphics.Outline;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import com.hivi.launcher.R;

final class SimpleClockWallpaperAdapter extends RecyclerView.Adapter<
        SimpleClockWallpaperAdapter.WallpaperViewHolder> {
    private static final int[] WALLPAPERS = {
            R.drawable.img_simple_clock_style1,
            R.drawable.img_simple_clock_style2,
            R.drawable.img_simple_clock_style3,
            R.drawable.img_simple_clock_style4,
            R.drawable.img_simple_clock_style5,
            R.drawable.img_simple_clock_style6,
            R.drawable.img_simple_clock_style7
    };

    interface Listener {
        void onWallpaperSelected(int wallpaper);
    }

    private final int mSelectedWallpaper;
    private final Listener mListener;

    SimpleClockWallpaperAdapter(int selectedWallpaper, Listener listener) {
        mSelectedWallpaper = selectedWallpaper;
        mListener = listener;
    }

    @Override
    public WallpaperViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.item_simple_clock_wallpaper, parent, false);
        return new WallpaperViewHolder(view);
    }

    @Override
    public void onBindViewHolder(WallpaperViewHolder holder, int position) {
        boolean selected = position == mSelectedWallpaper;
        holder.itemView.setSelected(selected);
        holder.image.setImageResource(WALLPAPERS[position]);
        holder.check.setVisibility(selected ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(view -> mListener.onWallpaperSelected(position));
    }

    @Override
    public int getItemCount() {
        return WALLPAPERS.length;
    }

    static final class WallpaperViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final ImageView check;

        WallpaperViewHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.simple_clock_wallpaper_image);
            check = itemView.findViewById(R.id.simple_clock_wallpaper_check);
            image.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    float density = view.getResources().getDisplayMetrics().density;
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 13f * density);
                }
            });
            image.setClipToOutline(true);
        }
    }
}
