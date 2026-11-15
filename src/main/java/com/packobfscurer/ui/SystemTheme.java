package com.packobfscurer.ui;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Reads whether the operating system is using a dark theme.
 *
 * Java has no API for this, so each OS is asked with its own built-in tool. Any failure, or an
 * OS that is not recognised, falls back to light.
 */
final class SystemTheme {

    private SystemTheme() {
    }

    static Theme.Mode detect() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                return windows();
            }
            if (os.contains("mac")) {
                return macos();
            }
            return linux();
        } catch (IOException | InterruptedException | RuntimeException e) {
            return Theme.Mode.LIGHT;
        }
    }

    /** AppsUseLightTheme is 0 when dark mode is on. */
    private static Theme.Mode windows() throws IOException, InterruptedException {
        String out = run(List.of("reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme"));
        return out.contains("0x0") ? Theme.Mode.DARK : Theme.Mode.LIGHT;
    }

    /** AppleInterfaceStyle is "Dark" in dark mode, and the key does not exist in light mode. */
    private static Theme.Mode macos() throws IOException, InterruptedException {
        String out = run(List.of("defaults", "read", "-g", "AppleInterfaceStyle"));
        return out.toLowerCase(Locale.ROOT).contains("dark") ? Theme.Mode.DARK : Theme.Mode.LIGHT;
    }

    /** GNOME exposes color-scheme. Other desktops are checked by their GTK theme name. */
    private static Theme.Mode linux() throws IOException, InterruptedException {
        String scheme = run(List.of("gsettings", "get", "org.gnome.desktop.interface", "color-scheme"));
        if (scheme.contains("prefer-dark")) {
            return Theme.Mode.DARK;
        }
        if (scheme.contains("prefer-light") || scheme.contains("default")) {
            return Theme.Mode.LIGHT;
        }
        String gtk = run(List.of("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme"));
        String env = System.getenv().getOrDefault("GTK_THEME", "");
        return (gtk + " " + env).toLowerCase(Locale.ROOT).contains("dark") ? Theme.Mode.DARK : Theme.Mode.LIGHT;
    }

    /** Runs a tool and returns its output. A missing tool or a timeout gives an empty string. */
    private static String run(List<String> command) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!p.waitFor(2, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return "";
        }
        return new String(p.getInputStream().readAllBytes()).trim();
    }
}
