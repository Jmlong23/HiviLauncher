package com.hivi.launcher.systemapps.model;

import com.hivi.launcher.R;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SupportedAppTest {
    @Test
    public void catalogUsesTheSuppliedPngIcons() {
        int[] expectedIcons = {
                R.drawable.ic_app_qq_music, R.drawable.ic_app_wyy, R.drawable.ic_app_kugou,
                R.drawable.ic_app_soda_music, R.drawable.ic_app_kge, R.drawable.ic_app_kuwo_music,
                R.drawable.ic_app_ximalaya, R.drawable.ic_app_qingting_fm, R.drawable.ic_app_lazy_audio
        };
        for (int index = 0; index < SupportedApp.values().length; index++) {
            assertEquals(expectedIcons[index], SupportedApp.values()[index].getIconResId());
        }
    }

    @Test
    public void catalogContainsOnlyNineAppsInReferenceOrder() {
        assertArrayEquals(new SupportedApp[] {
                SupportedApp.QQ_MUSIC, SupportedApp.NETEASE_MUSIC, SupportedApp.KUGOU_MUSIC,
                SupportedApp.SODA_MUSIC, SupportedApp.KARAOKE, SupportedApp.KUWO_MUSIC,
                SupportedApp.XIMALAYA, SupportedApp.QINGTING_FM, SupportedApp.LANREN_AUDIO
        }, SupportedApp.values());
    }

    @Test
    public void everyAppHasAnIconLabelDownloadSourceAndUniquePackages() {
        Set<String> packages = new HashSet<>();
        for (SupportedApp app : SupportedApp.values()) {
            assertTrue(app.getLabelResId() != 0);
            assertTrue(app.getIconResId() != 0);
            assertTrue(app.getDownloadUrlResId() != 0);
            assertFalse(app.getServerType().isEmpty());
            assertTrue(app.getPackageNames().length > 0);
            for (String packageName : app.getPackageNames()) {
                assertTrue(packages.add(packageName));
                assertTrue(app.matchesPackage(packageName));
            }
        }
    }

    @Test
    public void unrelatedPackagesCannotBeInstalledAsCatalogApps() {
        for (SupportedApp app : SupportedApp.values()) {
            assertFalse(app.matchesPackage("com.android.settings"));
            assertFalse(app.matchesPackage("com.hivi.launcher"));
            assertFalse(app.matchesPackage(null));
        }
    }

    @Test
    public void packageAliasesIncludeDeviceSpecificVariantsAndCannotBeMutated() {
        String[] packages = SupportedApp.QQ_MUSIC.getPackageNames();
        assertEquals("com.tencent.qqmusiccar", packages[0]);
        packages[0] = "other.package";
        assertTrue(SupportedApp.QQ_MUSIC.matchesPackage("com.tencent.qqmusiccar"));
        assertTrue(SupportedApp.QQ_MUSIC.matchesPackage("com.tencent.qqmusicpad"));
        assertTrue(SupportedApp.KARAOKE.matchesPackage("com.tencent.karaoketv"));
        assertTrue(SupportedApp.KUWO_MUSIC.matchesPackage("cn.kuwo.kwauto"));
        assertTrue(SupportedApp.KUWO_MUSIC.matchesPackage("cn.kuwo.kwmusichd"));
        assertTrue(SupportedApp.XIMALAYA.matchesPackage("com.ximalaya.ting.android.car"));
        assertTrue(SupportedApp.LANREN_AUDIO.matchesPackage("bubei.tingshu.hd"));
    }

    @Test
    public void serverTypesMatchTheStreamCatalog() {
        String[] expectedTypes = {"QQ-MUSIC", "NETEASE-MUSIC", "KUGOU-MUSIC", "SODA-MUSIC",
                "WE-SING", "KUWO-MUSIC", "XIMALAYA", "QINGTING-FM", "LAZY-AUDIO"};
        for (int index = 0; index < SupportedApp.values().length; index++) {
            assertEquals(expectedTypes[index], SupportedApp.values()[index].getServerType());
        }
    }

    @Test
    public void qqMusicIsProtectedWhileOtherCatalogAppsAreUninstallable() {
        for (SupportedApp app : SupportedApp.values()) {
            assertEquals(app != SupportedApp.QQ_MUSIC, app.isUninstallable());
        }
    }
}
