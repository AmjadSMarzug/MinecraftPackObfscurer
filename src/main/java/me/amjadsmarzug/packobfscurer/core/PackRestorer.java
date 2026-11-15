package me.amjadsmarzug.packobfscurer.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/**
 * Undoes an obfuscation using its version 2 mapping file.
 *
 * Restored: asset names, namespace names, references to both, and removal of junk.
 * Not restored, because the mapping does not record them: cube and group names, deleted groups,
 * author credits, PNG and Vorbis metadata, minified formatting, and the pack description override.
 * The output is written in normal formatting.
 */
public final class PackRestorer {

    /** Notes listed to the user after every restore. */
    public static final List<String> NOT_RESTORED = List.of(
            "cube and group names (they were replaced, not recorded)",
            "deleted groups",
            "removed author credits",
            "removed PNG and Vorbis metadata",
            "minified JSON formatting (restored output is pretty-printed)",
            "pack description override");

    public record Result(SortedMap<String, byte[]> files, int restoredFiles, int removedJunk, List<String> notes) {
    }

    private PackRestorer() {
    }

    @SuppressWarnings("unchecked")
    public static Result restore(SortedMap<String, byte[]> obfuscated, String mappingJson) throws IOException {
        Map<String, Object> mapping;
        try {
            Object parsed = Json.parse(mappingJson);
            if (!(parsed instanceof Map<?, ?> m)) {
                throw new IOException("The mapping file is not a JSON object.");
            }
            mapping = (Map<String, Object>) m;
        } catch (Json.ParseException e) {
            throw new IOException("The mapping file is not valid JSON: " + e.getMessage(), e);
        }
        if (!"packobfuscator-mapping".equals(mapping.get("format"))) {
            throw new IOException("This is not a PackObfuscator mapping file.");
        }
        Object version = mapping.get("version");
        int versionNumber = version instanceof Json.Raw raw ? Integer.parseInt(raw.text()) : 0;
        if (versionNumber < 2) {
            throw new IOException("This mapping is from an older version and has no file list, so it cannot restore the pack.");
        }

        Map<String, String> nsInverse = invert(stringMap(mapping.get("namespaces")));
        Map<String, String> fileInverse = invert(stringMap(mapping.get("files")));
        Set<String> junk = new HashSet<>();
        if (mapping.get("junk") instanceof List<?> list) {
            for (Object o : list) {
                junk.add(String.valueOf(o));
            }
        }

        // Every asset in the obfuscated pack, and which original asset each new asset came from.
        Set<AssetKey> owned = new HashSet<>();
        for (String path : obfuscated.keySet()) {
            AssetKey.fromZipPath(path).ifPresent(owned::add);
        }
        Map<AssetKey, AssetKey> assetInverse = new HashMap<>();
        // fileInverse maps a new path to its original path. Build new key -> original key.
        for (Map.Entry<String, String> e : fileInverse.entrySet()) {
            AssetKey newKey = AssetKey.fromZipPath(e.getKey()).orElse(null);
            AssetKey originalKey = AssetKey.fromZipPath(e.getValue()).orElse(null);
            if (newKey != null && originalKey != null) {
                assetInverse.put(newKey, originalKey);
            }
        }
        UnaryOperator<AssetKey> resolver = k -> assetInverse.getOrDefault(k, k);

        SortedMap<String, byte[]> out = new TreeMap<>();
        int restored = 0;
        int removedJunk = 0;
        for (Map.Entry<String, byte[]> e : obfuscated.entrySet()) {
            String path = e.getKey();
            if (junk.contains(path)) {
                removedJunk++;
                continue;
            }
            String original = NamespaceRewriter.movePath(fileInverse.getOrDefault(path, path), nsInverse);
            if (!original.equals(path)) {
                restored++;
            }
            byte[] bytes = e.getValue();
            if (path.startsWith("assets/") && (path.endsWith(".json") || path.endsWith(".mcmeta"))) {
                Object doc = Json.parse(new String(bytes, StandardCharsets.UTF_8));
                doc = new ReferenceRewriter(resolver, owned, path, new ArrayList<>()).rewrite(doc);
                doc = NamespaceRewriter.rewrite(doc, nsInverse);
                bytes = PackSource.utf8(Json.write(doc, true));
            }
            out.put(original, bytes);
        }
        return new Result(out, restored, removedJunk, NOT_RESTORED);
    }

    private static Map<String, String> stringMap(Object value) {
        Map<String, String> out = new HashMap<>();
        if (value instanceof Map<?, ?> m) {
            m.forEach((k, v) -> out.put(String.valueOf(k), String.valueOf(v)));
        }
        return out;
    }

    private static Map<String, String> invert(Map<String, String> map) {
        Map<String, String> out = new HashMap<>();
        map.forEach((k, v) -> out.put(v, k));
        return out;
    }
}
