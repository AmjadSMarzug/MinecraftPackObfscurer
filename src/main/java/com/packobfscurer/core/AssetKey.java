package com.packobfscurer.core;

import java.util.Optional;

/**
 * One pack asset, identified the way the game refers to it: namespace plus path without extension.
 * Example: models "minecraft:item/gauntlet" is the file assets/minecraft/models/item/gauntlet.json.
 */
public record AssetKey(Type type, String namespace, String path) {

    public enum Type {
        MODEL("models", ".json"),
        TEXTURE("textures", ".png"),
        SOUND("sounds", ".ogg");

        final String folder;
        final String extension;

        Type(String folder, String extension) {
            this.folder = folder;
            this.extension = extension;
        }
    }

    /** Path of the file inside the pack zip, e.g. assets/minecraft/models/item/gauntlet.json. */
    public String zipPath() {
        return "assets/" + namespace + "/" + type.folder + "/" + path + type.extension;
    }

    /** Path relative to assets/, used for the protected-folder check. */
    public String assetRelative() {
        return namespace + "/" + type.folder + "/" + path;
    }

    public String refId() {
        return namespace + ":" + path;
    }

    /** Parses a file path inside the pack into an asset, if the file is a model, texture or sound. */
    public static Optional<AssetKey> fromZipPath(String zipPath) {
        if (!zipPath.startsWith("assets/")) {
            return Optional.empty();
        }
        String[] parts = zipPath.split("/", 4);
        if (parts.length < 4) {
            return Optional.empty();
        }
        String ns = parts[1];
        String folder = parts[2];
        String rest = parts[3];
        for (Type t : Type.values()) {
            if (t.folder.equals(folder) && rest.endsWith(t.extension) && rest.length() > t.extension.length()) {
                return Optional.of(new AssetKey(t, ns, rest.substring(0, rest.length() - t.extension.length())));
            }
        }
        return Optional.empty();
    }
}
