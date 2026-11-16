package me.amjadsmarzug.packobfscurer.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/** Ties the checker, download, checksum and installer together. Used by both the window and the CLI. */
public final class UpdateService {

    /** What a check found. */
    public record Status(Version current, Optional<Release> latest) {
        public boolean updateAvailable() {
            return latest.isPresent() && latest.get().version().compareTo(current) > 0;
        }
    }

    private final UpdateChecker checker;
    private final String currentVersion;

    public UpdateService(String currentVersion, String repo, String token) {
        this.currentVersion = currentVersion;
        this.checker = new UpdateChecker(repo, token);
    }

    /** The version this copy was built as (from the jar manifest), or 1.0.0 when run from source. */
    public static String currentVersion() {
        String v = UpdateService.class.getPackage() == null ? null : UpdateService.class.getPackage().getImplementationVersion();
        return v == null ? "1.0.0" : v;
    }

    public Status check() throws IOException, InterruptedException {
        Version current = Version.parse(currentVersion).orElseThrow(() -> new IOException("unknown version " + currentVersion));
        return new Status(current, checker.latest());
    }

    /** Downloads the installer for this machine into a temp folder, checks it against SHA256SUMS, and returns it. */
    public Path download(Release release, Installers.Kind kind, Consumer<String> log) throws IOException, InterruptedException {
        Release.Asset asset = Installers.pick(release, kind);
        if (asset == null) {
            throw new IOException("No installer for this system in release " + release.tag() + ".");
        }
        Release.Asset sums = release.asset("SHA256SUMS.txt");
        if (sums == null) {
            throw new IOException("Release " + release.tag() + " has no SHA256SUMS.txt, so the download cannot be checked.");
        }
        Path dir = Files.createTempDirectory("packobfuscator-update");
        Path sumsFile = dir.resolve("SHA256SUMS.txt");
        checker.download(sums, sumsFile);
        String expected = Installers.expectedHash(Files.readString(sumsFile, StandardCharsets.UTF_8), asset.name());
        Path file = dir.resolve(asset.name());
        log.accept("Downloading " + asset.name() + " (" + asset.size() / (1024 * 1024) + " MB)...");
        checker.download(asset, file);
        Installers.verify(file, expected);
        log.accept("Checksum OK.");
        return file;
    }
}
