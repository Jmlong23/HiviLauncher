package com.hivi.launcher.systemapps.model;

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
}
