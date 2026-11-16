package me.amjadsmarzug.packobfscurer.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Picks the installer for this machine, checks it, and starts it. */
public final class Installers {

    /** How this copy of the app was installed. Decides which update is possible. */
    public enum Kind { WINDOWS, MACOS, LINUX_RPM, OTHER }

    private Installers() {
    }

    public static Kind detect() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return Kind.WINDOWS;
        }
        if (os.contains("mac")) {
            return Kind.MACOS;
        }
        return rpmInstalled() ? Kind.LINUX_RPM : Kind.OTHER;
    }

    /** The asset to download for this kind of install, or null when there is none (portable Linux builds). */
    public static Release.Asset pick(Release release, Kind kind) {
        String want = switch (kind) {
            case WINDOWS -> ".exe";
            case MACOS -> ".dmg";
            case LINUX_RPM -> ".rpm";
            case OTHER -> null;
        };
        if (want == null) {
            return null;
        }
        return release.assets().stream()
                .filter(a -> a.name().toLowerCase(Locale.ROOT).endsWith(want))
                .findFirst().orElse(null);
    }

    /** Looks up the expected SHA-256 for a file name in a SHA256SUMS listing ("hash  name" per line). */
    public static String expectedHash(String sums, String fileName) {
        for (String line : sums.split("\n")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 2 && parts[1].equals(fileName)) {
                return parts[0].toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    public static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Throws unless the file matches the published hash. A file that cannot be checked is never run. */
    public static void verify(Path file, String expectedHash) throws IOException {
        if (expectedHash == null) {
            throw new IOException("No checksum published for " + file.getFileName() + ", so it will not be installed.");
        }
        String actual = sha256(file);
        if (!actual.equals(expectedHash)) {
            throw new IOException("Checksum mismatch for " + file.getFileName() + ". The download is not used.");
        }
    }

    /**
     * Starts the installer. Windows and macOS open their installer for the user to finish, because
     * these builds are not code-signed. Linux runs rpm through pkexec, which asks for the password.
     */
    public static void start(Path file, Kind kind) throws IOException, InterruptedException {
        switch (kind) {
            case WINDOWS -> new ProcessBuilder(file.toAbsolutePath().toString()).start();
            case MACOS -> new ProcessBuilder("open", file.toAbsolutePath().toString()).start();
            case LINUX_RPM -> {
                Process p = new ProcessBuilder("pkexec", "rpm", "-Uvh", "--replacepkgs", file.toAbsolutePath().toString())
                        .inheritIO().start();
                if (!p.waitFor(10, TimeUnit.MINUTES) || p.exitValue() != 0) {
                    throw new IOException("rpm did not finish the update (exit " + p.exitValue() + ").");
                }
            }
            case OTHER -> throw new IOException("This install cannot be updated automatically. Download the new version from the release page.");
        }
    }

    static boolean rpmInstalled() {
        try {
            Process p = new ProcessBuilder("rpm", "-q", "packobfuscator").redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
