package me.amjadsmarzug.packobfscurer.core;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Gives every part (element) of a real model its own PNG. Each new PNG has the same size as the
 * original, keeps only the pixels that the part's faces sample, and fills every other pixel with noise.
 * The rendered result is unchanged.
 *
 * UV coordinates are scaled to the texture with the model's texture_size (16 if it has none). A model
 * with no texture_size is only handled when its textures are 16x16, because then the scale is not in
 * doubt. Anything the splitter cannot prove safe is left exactly as it was: a model with a parent in
 * the pack, a model that is a parent of another, a face without UVs, or a texture used somewhere else
 * (an atlas, a font, a particle) or that has animation metadata.
 */
final class TextureSplitter {

    private static final int DEFAULT_SCALE = 16;

    record Result(int newTextures, int removedTextures, int splitModels, int skippedModels) {
    }

    /** One face of a part: its name, its texture key in the model, its texture ref, and its UV box in model units. */
    private record Face(String faceName, String ref, double[] uv) {
    }

    /** One element (part) of a model, with the scale its UVs are measured in. */
    private record Part(String modelPath, int index, int scaleX, int scaleY, boolean explicitScale, List<Face> faces) {
        Set<String> refs() {
            Set<String> out = new HashSet<>();
            for (Face f : faces) {
                out.add(f.ref());
            }
            return out;
        }
    }

    private TextureSplitter() {
    }

    static Result split(SortedMap<String, byte[]> out, Random rnd, boolean minify) {
        // Parse every JSON file under assets. A file that does not parse is left alone.
        Map<String, Object> docs = new TreeMap<>();
        for (Map.Entry<String, byte[]> e : out.entrySet()) {
            String path = e.getKey();
            if (path.startsWith("assets/") && path.endsWith(".json")) {
                try {
                    docs.put(path, Json.parse(new String(e.getValue(), StandardCharsets.UTF_8)));
                } catch (Json.ParseException ignored) {
                    // not ours to touch
                }
            }
        }

        Map<String, String> modelRefs = new HashMap<>();
        for (String path : docs.keySet()) {
            String ref = modelRef(path);
            if (ref != null) {
                modelRefs.put(path, ref);
            }
        }
        Set<String> parentsInPack = new HashSet<>();
        for (String path : modelRefs.keySet()) {
            if (docs.get(path) instanceof Map<?, ?> m && m.get("parent") instanceof String parent) {
                parentsInPack.add(norm(parent));
            }
        }

        Set<String> external = new HashSet<>();
        List<Part> parts = new ArrayList<>();
        Map<String, Map<String, String>> textureMaps = new HashMap<>();
        int skippedModels = 0;
        for (String path : modelRefs.keySet()) {
            if (!(docs.get(path) instanceof Map<?, ?> model)) {
                continue;
            }
            Map<String, String> keys = textureMap(model);
            textureMaps.put(path, keys);
            if (!modelEligible(model, modelRefs.get(path), parentsInPack)) {
                skippedModels++;
                external.addAll(keys.values());
                continue;
            }
            collectParts(path, model, keys, parts, external);
        }
        // Anything a non-model file names is out of reach.
        for (Map.Entry<String, Object> e : docs.entrySet()) {
            if (!modelRefs.containsKey(e.getKey())) {
                collectStrings(e.getValue(), external);
            }
        }

        Set<String> eligibleTextures = eligibleTextures(out, parts, external);
        List<Part> eligibleParts = new ArrayList<>();
        for (Part p : parts) {
            if (eligibleTextures.containsAll(p.refs())) {
                eligibleParts.add(p);
            }
        }

        Map<String, List<Part>> byModel = new TreeMap<>();
        for (Part p : eligibleParts) {
            byModel.computeIfAbsent(p.modelPath(), k -> new ArrayList<>()).add(p);
        }
        int newTextures = 0;
        int splitModels = 0;
        Set<String> sourceRefs = new HashSet<>();
        for (Map.Entry<String, List<Part>> e : byModel.entrySet()) {
            Map<String, Object> model = copyOf((Map<?, ?>) docs.get(e.getKey()));
            Map<String, String> oldKeys = textureMaps.get(e.getKey());
            Map<String, String> newKeys = new LinkedHashMap<>();
            List<Object> elements = new ArrayList<>((List<?>) model.get("elements"));
            for (Part part : e.getValue()) {
                Map<String, Object> element = copyOf((Map<?, ?>) elements.get(part.index()));
                Map<String, String> keyForRef = new HashMap<>();
                for (Face f : part.faces()) {
                    if (!keyForRef.containsKey(f.ref())) {
                        String key = uniqueKey(newKeys, oldKeys, part.index(), keyForRef.size());
                        newKeys.put(key, writeTexture(out, f.ref(), part, rnd));
                        keyForRef.put(f.ref(), key);
                        sourceRefs.add(f.ref());
                        newTextures++;
                    }
                }
                Map<String, Object> faces = copyOf((Map<?, ?>) element.get("faces"));
                for (Face f : part.faces()) {
                    Map<String, Object> face = copyOf((Map<?, ?>) faces.get(f.faceName()));
                    face.put("texture", "#" + keyForRef.get(f.ref()));
                    faces.put(f.faceName(), face);
                }
                element.put("faces", faces);
                elements.set(part.index(), element);
            }
            model.put("elements", elements);

            // Keep the old texture entries that unsplit elements still use. Drop the rest.
            String json = Json.write(model, true);
            Map<String, Object> textures = new LinkedHashMap<>();
            for (Map.Entry<String, String> old : oldKeys.entrySet()) {
                if (json.contains("\"#" + old.getKey() + "\"")) {
                    textures.put(old.getKey(), old.getValue());
                }
            }
            textures.putAll(newKeys);
            model.put("textures", textures);
            docs.put(e.getKey(), model);
            splitModels++;
        }

        for (String path : byModel.keySet()) {
            out.put(path, PackSource.utf8(Json.write(docs.get(path), !minify)));
        }

        // Remove a split texture only if nothing refers to it any more.
        Set<String> stillUsed = new HashSet<>();
        for (Object doc : docs.values()) {
            collectStrings(doc, stillUsed);
        }
        int removed = 0;
        for (String ref : sourceRefs) {
            if (!stillUsed.contains(ref) && out.remove(textureZipPath(ref)) != null) {
                removed++;
            }
        }
        return new Result(newTextures, removed, splitModels, skippedModels);
    }

    // ------------------------------------------------------------------ analysis

    private static boolean modelEligible(Map<?, ?> model, String ref, Set<String> parentsInPack) {
        if (!(model.get("elements") instanceof List<?> elements) || elements.isEmpty()) {
            return false;
        }
        if (model.get("parent") instanceof String parent && !parent.startsWith("minecraft:")) {
            return false;
        }
        return !parentsInPack.contains(ref);
    }

    /** Records every part of a model that has UVs on every face. Other parts put their textures in external. */
    private static void collectParts(String path, Map<?, ?> model, Map<String, String> keys, List<Part> parts,
                                     Set<String> external) {
        int sx = DEFAULT_SCALE;
        int sy = DEFAULT_SCALE;
        boolean explicit = false;
        if (model.get("texture_size") instanceof List<?> size && size.size() == 2
                && size.get(0) instanceof Json.Raw w && size.get(1) instanceof Json.Raw h) {
            sx = Integer.parseInt(w.text());
            sy = Integer.parseInt(h.text());
            explicit = true;
        }
        if (sx <= 0 || sy <= 0) {
            return;
        }
        if (!(model.get("elements") instanceof List<?> elements)) {
            return;
        }
        for (int i = 0; i < elements.size(); i++) {
            if (!(elements.get(i) instanceof Map<?, ?> el) || !(el.get("faces") instanceof Map<?, ?> faces)
                    || faces.isEmpty()) {
                markExternal(keys, external);
                continue;
            }
            List<Face> list = new ArrayList<>();
            boolean ok = true;
            for (Map.Entry<?, ?> fe : faces.entrySet()) {
                double[] uv = null;
                String ref = null;
                if (fe.getValue() instanceof Map<?, ?> face && face.get("texture") instanceof String tex
                        && tex.startsWith("#") && keys.containsKey(tex.substring(1))
                        && face.get("uv") instanceof List<?> list4 && list4.size() == 4) {
                    uv = uvOf(list4);
                    ref = keys.get(tex.substring(1));
                }
                if (uv == null || ref == null) {
                    ok = false;
                    break;
                }
                list.add(new Face(String.valueOf(fe.getKey()), ref, uv));
            }
            if (ok) {
                parts.add(new Part(path, i, sx, sy, explicit, list));
            } else {
                markExternal(keys, external);
            }
        }
    }

    private static void markExternal(Map<String, String> keys, Set<String> external) {
        external.addAll(keys.values());
    }

    /** A texture is eligible if it is a PNG with no animation metadata and every use of it is an eligible part. */
    private static Set<String> eligibleTextures(SortedMap<String, byte[]> out, List<Part> parts, Set<String> external) {
        Set<String> candidates = new HashSet<>();
        for (Part p : parts) {
            for (Face f : p.faces()) {
                String zip = textureZipPath(f.ref());
                if (external.contains(f.ref()) || zip == null || !out.containsKey(zip)
                        || out.containsKey(zip + ".mcmeta")) {
                    continue;
                }
                BufferedImage img = decode(out.get(zip));
                if (img == null) {
                    continue;
                }
                // Without texture_size the scale is only certain for a 16x16 texture.
                if (p.explicitScale() || (img.getWidth() == DEFAULT_SCALE && img.getHeight() == DEFAULT_SCALE)) {
                    candidates.add(f.ref());
                }
            }
        }
        candidates.removeAll(external);

        // Fixed point: a part is eligible only if all its textures are eligible, and a texture only if
        // every part that uses it is eligible.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Part p : parts) {
                if (!candidates.containsAll(p.refs())) {
                    for (String ref : p.refs()) {
                        if (candidates.remove(ref)) {
                            changed = true;
                        }
                    }
                }
            }
        }
        return candidates;
    }

    // ------------------------------------------------------------------ writing

    /** Writes a new PNG, the same size as the source, for one part's use of one texture. Returns its ref. */
    private static String writeTexture(SortedMap<String, byte[]> out, String sourceRef, Part part, Random rnd) {
        BufferedImage source = decode(out.get(textureZipPath(sourceRef)));
        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, 0xFF000000 | rnd.nextInt(0x1000000));
            }
        }
        for (Face f : part.faces()) {
            if (!f.ref().equals(sourceRef)) {
                continue;
            }
            int[] r = pixelBox(f.uv(), part, w, h);
            for (int y = r[1]; y < r[3]; y++) {
                for (int x = r[0]; x < r[2]; x++) {
                    img.setRGB(x, y, source.getRGB(x, y));
                }
            }
        }
        String ns = sourceRef.substring(0, sourceRef.indexOf(':'));
        String rel = sourceRef.substring(sourceRef.indexOf(':') + 1);
        String dir = rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/') + 1) : "";
        String name;
        String zipPath;
        do {
            name = dir + String.format("%08x", rnd.nextInt());
            zipPath = "assets/" + ns + "/textures/" + name + ".png";
        } while (out.containsKey(zipPath));
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", bos);
            out.put(zipPath, bos.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return ns + ":" + name;
    }

    /**
     * The pixel box a UV box covers: x1, y1 inclusive and x2, y2 exclusive. Rounded outward, so it
     * always includes every pixel the face can sample.
     */
    static int[] pixelBox(double[] uv, Part part, int w, int h) {
        double sx = w / (double) part.scaleX();
        double sy = h / (double) part.scaleY();
        int x1 = (int) Math.floor(Math.min(uv[0], uv[2]) * sx);
        int x2 = (int) Math.ceil(Math.max(uv[0], uv[2]) * sx);
        int y1 = (int) Math.floor(Math.min(uv[1], uv[3]) * sy);
        int y2 = (int) Math.ceil(Math.max(uv[1], uv[3]) * sy);
        return new int[]{Math.max(0, x1), Math.max(0, y1), Math.min(w, x2), Math.min(h, y2)};
    }

    // ------------------------------------------------------------------ helpers

    private static String uniqueKey(Map<String, String> newKeys, Map<String, String> oldKeys, int index, int j) {
        String key = "p" + index + "_" + j;
        int n = 0;
        while (newKeys.containsKey(key) || oldKeys.containsKey(key)) {
            key = "p" + index + "_" + j + "_" + (++n);
        }
        return key;
    }

    private static String modelRef(String path) {
        if (!path.startsWith("assets/") || !path.endsWith(".json")) {
            return null;
        }
        String[] parts = path.split("/", 4);
        if (parts.length < 4 || !parts[2].equals("models")) {
            return null;
        }
        return parts[1] + ":" + parts[3].substring(0, parts[3].length() - 5);
    }

    private static Map<String, String> textureMap(Map<?, ?> model) {
        Map<String, String> keys = new LinkedHashMap<>();
        if (model.get("textures") instanceof Map<?, ?> tex) {
            for (Map.Entry<?, ?> e : tex.entrySet()) {
                if (e.getValue() instanceof String v && !v.startsWith("#") && norm(v) != null) {
                    keys.put(String.valueOf(e.getKey()), norm(v));
                }
            }
        }
        return keys;
    }

    /** "ns:path" with no extension. A bare path is in minecraft. Null for variables. */
    static String norm(String ref) {
        if (ref == null || ref.startsWith("#")) {
            return null;
        }
        String r = ref.endsWith(".png") ? ref.substring(0, ref.length() - 4) : ref;
        return r.contains(":") ? r : "minecraft:" + r;
    }

    private static String textureZipPath(String ref) {
        if (ref == null || !ref.contains(":")) {
            return null;
        }
        int colon = ref.indexOf(':');
        return "assets/" + ref.substring(0, colon) + "/textures/" + ref.substring(colon + 1) + ".png";
    }

    private static double[] uvOf(List<?> uv) {
        double[] v = new double[4];
        for (int i = 0; i < 4; i++) {
            if (!(uv.get(i) instanceof Json.Raw raw)) {
                return null;
            }
            v[i] = Double.parseDouble(raw.text());
        }
        return v;
    }

    private static BufferedImage decode(byte[] png) {
        if (png == null) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            return null;
        }
    }

    /** Adds every string in a JSON value, as a normalised ref, to the set. */
    private static void collectStrings(Object node, Set<String> into) {
        if (node instanceof String s) {
            String n = norm(s);
            if (n != null) {
                into.add(n);
            }
        } else if (node instanceof List<?> list) {
            for (Object o : list) {
                collectStrings(o, into);
            }
        } else if (node instanceof Map<?, ?> map) {
            for (Object o : map.values()) {
                collectStrings(o, into);
            }
        }
    }

    private static Map<String, Object> copyOf(Map<?, ?> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }
}
