package me.amjadsmarzug.packobfscurer.update;

import java.util.List;

/** The bits of a GitHub release the updater needs. */
public record Release(String tag, Version version, String pageUrl, List<Asset> assets) {

    /**
     * @param apiUrl the asset's API URL. Downloading through it works for private repos when a token is sent.
     */
    public record Asset(String name, String apiUrl, long size) {
    }

    public Asset asset(String name) {
        return assets.stream().filter(a -> a.name().equals(name)).findFirst().orElse(null);
    }
}
