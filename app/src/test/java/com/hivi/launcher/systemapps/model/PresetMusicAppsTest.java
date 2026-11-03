package com.hivi.launcher.systemapps.model;

import com.hivi.launcher.R;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class PresetMusicAppsTest {
    @Test
    public void appsFollowReferenceGridOrder() {
        assertArrayEquals(new PresetMusicApps[] {
                PresetMusicApps.QQ_MUSIC,
                PresetMusicApps.NETEASE_CLOUD_MUSIC,
                PresetMusicApps.KUGOU_MUSIC,
                PresetMusicApps.SODA_MUSIC,
                PresetMusicApps.WE_SING,
                PresetMusicApps.KUWO_MUSIC,
                PresetMusicApps.XIMALAYA,
                PresetMusicApps.QINGTING_FM,
                PresetMusicApps.LAZY_AUDIO
        }, PresetMusicApps.values());
    }

    @Test
    public void appsUseTheirMatchingBrandIcons() {
        int[] actualIcons = new int[PresetMusicApps.values().length];
        int index = 0;
        for (PresetMusicApps app : PresetMusicApps.values()) {
            actualIcons[index++] = app.getIconResId();
        }
        assertArrayEquals(new int[] {
                R.drawable.ic_app_qq_music,
                R.drawable.ic_app_netease_music,
                R.drawable.ic_app_kugou_music,
                R.drawable.ic_app_soda_music,
                R.drawable.ic_app_we_sing,
                R.drawable.ic_app_kuwo_music,
                R.drawable.ic_app_ximalaya,
                R.drawable.ic_app_qingting_fm,
                R.drawable.ic_app_lazy_audio
        }, actualIcons);
    }
}
