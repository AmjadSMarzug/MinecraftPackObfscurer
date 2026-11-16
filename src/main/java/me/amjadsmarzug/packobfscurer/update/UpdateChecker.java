package me.amjadsmarzug.packobfscurer.update;

import me.amjadsmarzug.packobfscurer.core.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Asks GitHub for the latest release. Failures are reported as empty results, never as crashes. */
public final class UpdateChecker {

    /** The repository that publishes releases. */
    public static final String REPO = "AmjadSMarzug/me.amjadsmarzug.packobfscurer";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http;
    private final String token;
    private final String repo;

    public UpdateChecker(String repo, String token) {
        this.repo = repo;
        this.token = token;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /** Token from PACKOBF_UPDATE_TOKEN, if set. Needed only while the repository is private. */
    public static String tokenFromEnvironment() {
        String t = System.getenv("PACKOBF_UPDATE_TOKEN");
        return t == null || t.isBlank() ? null : t.trim();
    }

    public Optional<Release> latest() throws IOException, InterruptedException {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "PackObfuscator-updater");
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> res = http.send(req.GET().build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404) {
            return Optional.empty(); // no release yet, or the repo is private and no token was given
        }
        if (res.statusCode() != 200) {
            throw new IOException("GitHub answered HTTP " + res.statusCode());
        }
        return Optional.of(parse(res.body()));
    }

    /** Parses the release JSON. Package-private for tests. */
    static Release parse(String json) {
        Map<?, ?> root = (Map<?, ?>) Json.parse(json);
        String tag = String.valueOf(root.get("tag_name"));
        Version version = Version.parse(tag).orElseThrow(() -> new IllegalArgumentException("bad tag " + tag));
        List<Release.Asset> assets = new ArrayList<>();
        if (root.get("assets") instanceof List<?> list) {
            for (Object o : list) {
                Map<?, ?> a = (Map<?, ?>) o;
                long size = a.get("size") instanceof Json.Raw r ? Long.parseLong(r.text()) : 0;
                assets.add(new Release.Asset(String.valueOf(a.get("name")), String.valueOf(a.get("url")), size));
            }
        }
        String body = root.get("body") == null ? "" : String.valueOf(root.get("body")).trim();
        return new Release(tag, version, String.valueOf(root.get("html_url")), body, assets);
    }

    /** Downloads an asset to a file. Uses the API URL, so private repos work with a token. */
    public void download(Release.Asset asset, java.nio.file.Path target) throws IOException, InterruptedException {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(asset.apiUrl()))
                .timeout(Duration.ofMinutes(10))
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "PackObfuscator-updater");
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        HttpResponse<java.nio.file.Path> res = http.send(req.GET().build(),
                HttpResponse.BodyHandlers.ofFile(target));
        if (res.statusCode() != 200) {
            throw new IOException("download failed: HTTP " + res.statusCode());
        }
    }

    public String repo() {
        return repo;
    }
}
