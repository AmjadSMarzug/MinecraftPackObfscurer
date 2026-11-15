package com.packobfscurer.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renames namespaces inside JSON: "ns:path" strings and "ns:path" keys whose namespace is in the map.
 *
 * Runs after {@link ReferenceRewriter}, so the reference rewriter still sees the original names.
 * Strings whose namespace is not in the map (including every "minecraft:" string) are left alone.
 */
final class NamespaceRewriter {

    private NamespaceRewriter() {
    }

    static Object rewrite(Object node, Map<String, String> nsMap) {
        if (nsMap.isEmpty()) {
            return node;
        }
        if (node instanceof String s) {
            return renameRef(s, nsMap);
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(rewrite(o, nsMap));
            }
            return out;
        }
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(renameRef(String.valueOf(e.getKey()), nsMap), rewrite(e.getValue(), nsMap));
            }
            return out;
        }
        return node;
    }

    /** "ns:rest" becomes "newNs:rest" when ns is in the map. Anything else is returned unchanged. */
    static String renameRef(String s, Map<String, String> nsMap) {
        int colon = s.indexOf(':');
        if (colon <= 0) {
            return s;
        }
        String to = nsMap.get(s.substring(0, colon));
        return to == null ? s : to + s.substring(colon);
    }

    /** Moves "assets/ns/..." to "assets/newNs/..." when ns is in the map. */
    static String movePath(String path, Map<String, String> nsMap) {
        if (nsMap.isEmpty() || !path.startsWith("assets/")) {
            return path;
        }
        int slash = path.indexOf('/', "assets/".length());
        if (slash < 0) {
            return path;
        }
        String to = nsMap.get(path.substring("assets/".length(), slash));
        return to == null ? path : "assets/" + to + path.substring(slash);
    }
}
