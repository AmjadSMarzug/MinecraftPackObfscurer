package me.amjadsmarzug.packobfscurer.core;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Checks a pack for problems that would show up in game: invalid JSON, PNGs that do not decode,
 * sounds that are not Ogg files, and references to files the pack does not contain.
 *
 * References to vanilla (minecraft:) assets cannot be checked without the client jar, so they are skipped.
 */
public final class PackVerifier {

    private PackVerifier() {
    }

    /** Returns one message per problem, or an empty list if the pack looks sound. */
    public static List<String> verify(Map<String, byte[]> files) {
        List<String> problems = new ArrayList<>();
        if (!files.containsKey("pack.mcmeta")) {
            problems.add("pack.mcmeta: missing at the pack root");
        }
        Set<AssetKey> owned = new HashSet<>();
        for (String path : files.keySet()) {
            AssetKey.fromZipPath(path).ifPresent(owned::add);
        }
        UnaryOperator<AssetKey> identity = k -> k;

        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String path = e.getKey();
            byte[] bytes = e.getValue();
            if (path.endsWith("/")) {
                continue;
            }
            if (path.endsWith(".json") || path.endsWith(".mcmeta")) {
                try {
                    Object doc = Json.parse(new String(bytes, StandardCharsets.UTF_8));
                    if (path.startsWith("assets/")) {
                        List<String> unresolved = new ArrayList<>();
                        new ReferenceRewriter(identity, owned, path, unresolved).rewrite(doc);
                        for (String ref : unresolved) {
                            problems.add(path + ": reference " + ref + " has no matching file in the pack");
                        }
                    }
                } catch (Json.ParseException ex) {
                    problems.add(path + ": invalid JSON, " + ex.getMessage());
                }
            } else if (path.endsWith(".png") && path.startsWith("assets/")) {
                try {
                    if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
                        problems.add(path + ": PNG cannot be decoded");
                    }
                } catch (java.io.IOException ex) {
                    problems.add(path + ": PNG cannot be decoded (" + ex.getMessage() + ")");
                }
            } else if (path.endsWith(".ogg") && path.startsWith("assets/")) {
                if (bytes.length < 4 || !"OggS".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))) {
                    problems.add(path + ": not an Ogg file");
                }
            }
        }
        return problems;
    }
}
