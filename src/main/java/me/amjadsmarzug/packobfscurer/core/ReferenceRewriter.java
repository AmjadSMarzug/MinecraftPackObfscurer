package me.amjadsmarzug.packobfscurer.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Rewrites every reference to a renamed asset inside one JSON document.
 *
 * The meaning of a string depends on the key it sits under: "model" and "parent" point at models,
 * "textures" and "file" point at textures, "sounds" lists point at sounds. Only strings that name a
 * pack-owned asset are changed, so vanilla references and other text are left alone.
 */
final class ReferenceRewriter {

    private enum Ctx { NONE, MODEL, TEXTURE, SOUND }

    private final UnaryOperator<AssetKey> renames;
    private final Set<AssetKey> owned;
    private final boolean atlasFile;
    private final String fileName;
    private final List<String> unresolved;

    /**
     * @param renames  maps an owned asset to its new key (identity if it is not renamed)
     * @param owned    every model, texture and sound the pack actually contains
     * @param fileName file being rewritten, used in warnings
     * @param unresolved collects references to pack namespaces that point at nothing
     */
    ReferenceRewriter(UnaryOperator<AssetKey> renames, Set<AssetKey> owned, String fileName, List<String> unresolved) {
        this.renames = renames;
        this.owned = owned;
        this.fileName = fileName;
        this.unresolved = unresolved;
        this.atlasFile = fileName.contains("/atlases/");
    }

    Object rewrite(Object root) {
        return walk(root, Ctx.NONE);
    }

    private Object walk(Object node, Ctx ctx) {
        if (node instanceof String s) {
            return ctx == Ctx.NONE ? s : rewriteRef(s, ctx);
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(walk(o, ctx));
            }
            return out;
        }
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                out.put(key, walkEntry(key, e.getValue()));
            }
            return out;
        }
        return node;
    }

    private Object walkEntry(String key, Object value) {
        switch (key) {
            case "model":
            case "parent":
                return walk(value, Ctx.MODEL);
            case "textures":
                if (value instanceof Map<?, ?> m) {
                    // A texture map: every string value is a texture reference, e.g. "layer0": "ns:item/x".
                    Map<String, Object> out = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        out.put(String.valueOf(e.getKey()), walk(e.getValue(), Ctx.TEXTURE));
                    }
                    return out;
                }
                return walk(value, Ctx.TEXTURE);
            case "file":
                return walk(value, Ctx.TEXTURE);
            case "resource":
                return atlasFile ? walk(value, Ctx.TEXTURE) : walk(value, Ctx.NONE);
            case "sounds":
                return sounds(value);
            default:
                return walk(value, Ctx.NONE);
        }
    }

    /** A sound list holds either "ns:path" strings or objects with a "name" field. */
    private Object sounds(Object value) {
        if (!(value instanceof List<?> list)) {
            return walk(value, Ctx.NONE);
        }
        List<Object> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    String k = String.valueOf(e.getKey());
                    copy.put(k, k.equals("name") ? walk(e.getValue(), Ctx.SOUND) : walk(e.getValue(), Ctx.NONE));
                }
                out.add(copy);
            } else {
                out.add(walk(item, Ctx.SOUND));
            }
        }
        return out;
    }

    private String rewriteRef(String s, Ctx ctx) {
        if (s.isEmpty() || s.startsWith("#")) {
            return s; // empty, or a variable reference like "#layer0"
        }
        String ns = "minecraft";
        String path = s;
        boolean explicitNs = false;
        int colon = s.indexOf(':');
        if (colon >= 0) {
            ns = s.substring(0, colon);
            path = s.substring(colon + 1);
            explicitNs = true;
        }
        String suffix = "";
        if (ctx == Ctx.TEXTURE && path.endsWith(".png")) {
            suffix = ".png";
            path = path.substring(0, path.length() - 4);
        }
        AssetKey key = new AssetKey(switch (ctx) {
            case MODEL -> AssetKey.Type.MODEL;
            case SOUND -> AssetKey.Type.SOUND;
            default -> AssetKey.Type.TEXTURE;
        }, ns, path);

        if (!owned.contains(key)) {
            // Only report misses in the pack's own namespaces. Vanilla refs are expected to be absent.
            if (!ns.equals("minecraft")) {
                unresolved.add(s + " (in " + fileName + ")");
            }
            return s;
        }
        AssetKey target = renames.apply(key);
        String newPath = target.path() + suffix;
        return (explicitNs ? ns + ":" : "") + newPath;
    }
}
