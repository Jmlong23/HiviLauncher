package com.hivi.launcher.resources;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IconResourcesTest {
    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final Path RESOURCE_DIRECTORY = Paths.get("src", "main", "res");
    private static final Pattern DRAWABLE_REFERENCE =
            Pattern.compile("(?:@drawable/|R\\.drawable\\.)(\\w+)");

    @Test
    public void nativeActionIconsSkipDensityScaling() throws IOException {
        String[] iconNames = {"ic_settings", "ic_app", "ic_download", "ic_wifi",
                "ic_volume", "ic_volume_mute"};
        for (String iconName : iconNames) {
            assertBitmapSkipsDensityScaling(iconName + ".png");
        }
    }

    @Test
    public void statusBarKeepsOriginalCutoutsWithoutReplacementVectors() throws IOException {
        String layout = readSource(RESOURCE_DIRECTORY.resolve("layout/activity_main.xml"));
        String activity = readSource(RESOURCE_DIRECTORY.resolveSibling("java")
                .resolve("com/hivi/launcher/main/ui/MainActivity.java"));
        assertTrue(layout.contains("@drawable/ic_wifi"));
        assertTrue(layout.contains("@drawable/ic_volume"));
        assertTrue(activity.contains("? R.drawable.ic_volume_mute : R.drawable.ic_volume"));
        assertFalse(layout.contains("ic_status_"));
        assertFalse(activity.contains("ic_status_"));
        for (String name : new String[]{"wifi", "volume", "volume_mute"}) {
            assertFalse(Files.exists(RESOURCE_DIRECTORY.resolve(
                    "drawable/ic_status_" + name + ".xml")));
        }
    }

    @Test
    public void settingsAndDialogBitmapsSkipDensityScaling() throws IOException {
        Set<String> references = new HashSet<>(Arrays.asList("ic_wifi", "ic_wifi_off",
                "ic_volume", "ic_volume_mute", "ic_volume_sound_disabled",
                "main_home_status_background", "main_wifi_indicator"));
        try (Stream<Path> layouts = Files.list(RESOURCE_DIRECTORY.resolve("layout"))) {
            Path[] settingsLayouts = layouts.filter(path -> path.getFileName().toString().matches(
                    "(?:layout_settings_.*|popup_settings_.*|fragment_settings|"
                            + "dialog_(?:wifi_password|wifi_network_action|system_update_confirmation|"
                            + "system_update_progress|system_update_success|product_information|"
                            + "factory_reset|log_upload|simple_clock_wallpaper|volume)|"
                            + "item_(?:wifi_network|simple_clock_wallpaper))\\.xml"))
                    .toArray(Path[]::new);
            assertTrue(settingsLayouts.length > 0);
            for (Path layout : settingsLayouts) {
                references.addAll(findDrawableReferences(layout));
            }
        }
        Path javaRoot = RESOURCE_DIRECTORY.resolveSibling("java/com/hivi/launcher");
        try (Stream<Path> sources = Files.walk(javaRoot.resolve("settings"))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java"))
                    .toArray(Path[]::new)) {
                references.addAll(findDrawableReferences(source));
            }
        }
        references.addAll(findDrawableReferences(javaRoot.resolve("wifi/ui/WifiNetworkAdapter.java")));
        Map<String, List<Path>> resources = new HashMap<>();
        try (Stream<Path> files = Files.walk(RESOURCE_DIRECTORY)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getParent().getFileName().toString().startsWith("drawable"))
                    .forEach(path -> resources.computeIfAbsent(path.getFileName().toString()
                                    .replaceFirst("(\\.9)?\\.[^.]+$", ""),
                            ignored -> new ArrayList<>()).add(path));
        }
        Queue<String> pending = new ArrayDeque<>(references);
        Set<String> visited = new HashSet<>();
        int bitmapCount = 0;
        while (!pending.isEmpty()) {
            String name = pending.remove();
            if (!visited.add(name)) {
                continue;
            }
            List<Path> variants = resources.get(name);
            assertNotNull("Missing settings drawable: " + name, variants);
            for (Path variant : variants) {
                if (variant.toString().endsWith(".xml")) {
                    pending.addAll(findDrawableReferences(variant));
                } else {
                    assertBitmapSkipsDensityScaling(variant.getFileName().toString());
                    bitmapCount++;
                }
            }
        }
        assertTrue("Settings bitmap dependencies must be checked", bitmapCount > 0);
    }

    @Test
    public void homeShortcutsReuseUnscaledAppBrandIcons() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document layout = factory.newDocumentBuilder().parse(
                RESOURCE_DIRECTORY.resolve("layout/fragment_wifi.xml").toFile());
        String[][] shortcuts = {
                {"btn_qq_music", "ic_app_qq_music"},
                {"btn_wyy_music", "ic_app_netease_music"},
                {"btn_kugou_music", "ic_app_kugou_music"},
                {"btn_we_sing", "ic_app_we_sing"}
        };
        for (String[] shortcut : shortcuts) {
            Element icon = findShortcutIcon(layout, shortcut[0]);
            assertNotNull(shortcut[0] + " must keep its brand icon", icon);
            assertEquals("@drawable/" + shortcut[1],
                    icon.getAttributeNS(ANDROID_NAMESPACE, "src"));
            assertTrue(Files.isRegularFile(RESOURCE_DIRECTORY.resolve("drawable-nodpi")
                    .resolve(shortcut[1] + ".png")));
        }
    }

    @Test
    public void downloadOverlayDoesNotUpscaleTheOriginalBitmap() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document layout = factory.newDocumentBuilder().parse(
                RESOURCE_DIRECTORY.resolve("layout/item_app_grid.xml").toFile());
        NodeList icons = layout.getElementsByTagName("ImageView");
        for (int index = 0; index < icons.getLength(); index++) {
            Element icon = (Element) icons.item(index);
            if ("@+id/app_download".equals(icon.getAttributeNS(ANDROID_NAMESPACE, "id"))) {
                assertEquals("centerInside", icon.getAttributeNS(ANDROID_NAMESPACE, "scaleType"));
                assertEquals("42dp", icon.getAttributeNS(ANDROID_NAMESPACE, "layout_width"));
                assertEquals("42dp", icon.getAttributeNS(ANDROID_NAMESPACE, "layout_height"));
                return;
            }
        }
        throw new AssertionError("Download overlay must keep its icon");
    }

    private static void assertBitmapSkipsDensityScaling(String fileName) throws IOException {
        assertTrue(fileName + " must load without density downsampling",
                Files.isRegularFile(RESOURCE_DIRECTORY.resolve("drawable-nodpi").resolve(fileName)));
        try (Stream<Path> directories = Files.list(RESOURCE_DIRECTORY)) {
            assertFalse(fileName + " must not have a density-scaled duplicate",
                    directories.filter(Files::isDirectory)
                            .filter(directory -> directory.getFileName().toString()
                                    .startsWith("drawable"))
                            .filter(directory -> !"drawable-nodpi".equals(
                                    directory.getFileName().toString()))
                            .anyMatch(directory -> Files.exists(directory.resolve(fileName))));
        }
    }

    private static Set<String> findDrawableReferences(Path source) throws IOException {
        Set<String> references = new HashSet<>();
        Matcher matcher = DRAWABLE_REFERENCE.matcher(readSource(source));
        while (matcher.find()) {
            references.add(matcher.group(1));
        }
        return references;
    }

    private static String readSource(Path source) throws IOException {
        return new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
    }

    private static Element findShortcutIcon(Document layout, String shortcutId) {
        NodeList shortcuts = layout.getElementsByTagName("LinearLayout");
        for (int index = 0; index < shortcuts.getLength(); index++) {
            Element shortcut = (Element) shortcuts.item(index);
            if (("@+id/" + shortcutId).equals(
                    shortcut.getAttributeNS(ANDROID_NAMESPACE, "id"))) {
                return (Element) shortcut.getElementsByTagName("ImageView").item(0);
            }
        }
        return null;
    }
}
