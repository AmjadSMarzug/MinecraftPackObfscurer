package me.amjadsmarzug.packobfscurer.core;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Adds junk assets to a pack, the way junk classes work in a Java obfuscator.
 *
 * The junk is models, PNG textures, Ogg Vorbis sounds and empty folders that nothing in the game
 * refers to. The game only loads files it is pointed at, so none of it is ever used. It only makes
 * the pack harder to read.
 *
 * Every junk file is unique. Textures use random patterns and palettes. Models have random
 * geometry, faces and display settings. Sounds have their own pitch, length, waveform and envelope.
 * Each file's content is hashed, and anything that repeats is regenerated, so batch-deleting one
 * "junk" file type cannot match another.
 *
 * Junk goes under random folders inside the pack's own namespaces, so it looks like real content.
 */
final class JunkGenerator {

    private static final int[] TEXTURE_SIZES = {16, 32};
    private static final int MAX_ATTEMPTS = 200;
    private static final int MAX_REAL_TEXTURES = 256;
    private static final String[] PARENTS = {"minecraft:item/generated", "minecraft:item/handheld"};
    private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    /** Where a junk file lives: namespace, plus the path inside its type folder without extension. */
    private record Placed(String namespace, String relative, String zipPath) {
    }

    /** A junk texture other junk models may point at. */
    private record TextureRef(String ref, int size, Rect region) {
    }

    /** A rectangle in texture pixels: x1, y1 inclusive, x2, y2 exclusive. */
    private record Rect(int x1, int y1, int x2, int y2) {
    }

    record Result(SortedMap<String, byte[]> files, int models, int textures, int sounds, int folders, List<String> warnings) {
    }

    private final Random rnd;
    private final Set<String> existing;
    private final boolean minify;
    private final Optional<Path> ffmpeg;
    private final List<String> namespaces;
    private final SortedMap<String, byte[]> out = new TreeMap<>();
    private final Set<String> seen = new HashSet<>();
    private final List<String> warnings = new ArrayList<>();

    private final List<BufferedImage> realImages;
    private final List<TextureRef> hybrids = new ArrayList<>();
    /** Paths of textures a draft is using. They stay reserved even if the draft is rejected. */
    private final Set<String> reserved = new HashSet<>();
    private int partTextures;

    /** A junk model and the PNGs its parts use. Nothing is written until the draft is accepted. */
    private record Draft(byte[] model, Map<String, byte[]> textures) {
    }

    private JunkGenerator(Set<AssetKey> owned, Set<String> existing, Random rnd, boolean minify, Optional<Path> ffmpeg,
                          Map<String, byte[]> sourceFiles) {
        this.realImages = decodeRealTextures(sourceFiles);
        this.rnd = rnd;
        this.existing = existing;
        this.minify = minify;
        this.ffmpeg = ffmpeg;
        this.namespaces = new ArrayList<>(owned.stream().map(AssetKey::namespace).distinct().sorted().toList());
    }

    /**
     * @param existing every path already in the output, so junk never overwrites a real file
     * @param ffmpeg   the encoder for junk sounds, or empty if ffmpeg is not installed
     */
    /**
     * @param sourceFiles the pack's own files. Some junk textures copy real pixels from these, so that
     *                    part of a junk texture looks like the pack's art. Pass an empty map to skip that.
     */
    /**
     */
    static Result generate(PackOptions.Junk junk, Set<AssetKey> owned, Set<String> existing,
                           Random rnd, boolean minify, Optional<Path> ffmpeg, Map<String, byte[]> sourceFiles) {
        return new JunkGenerator(owned, existing, rnd, minify, ffmpeg, sourceFiles).run(junk);
    }

    private Result run(PackOptions.Junk junk) {
        if (junk.total() == 0) {
            return new Result(out, 0, 0, 0, 0, warnings);
        }
        if (namespaces.isEmpty()) {
            namespaces.add("minecraft");
        }

        // Textures first, so junk models have something to point at.
        for (int i = 0; i < junk.textures(); i++) {
            int size = TEXTURE_SIZES[rnd.nextInt(TEXTURE_SIZES.length)];
            // Roughly a third of the textures carry a real rectangle from the pack, and junk models reference those.
            boolean hybrid = !realImages.isEmpty() && rnd.nextInt(3) == 0;
            Placed p = place(pickNamespace(), "textures", ".png");
            Rect[] region = new Rect[1];
            byte[] content = unique(() -> hybrid ? pngWithRealPixels(size, region) : png(size));
            out.put(p.zipPath(), content);
            if (hybrid) {
                TextureRef ref = new TextureRef(p.namespace() + ":" + p.relative(), size, region[0]);
                hybrids.add(ref);
            }
        }
        for (int i = 0; i < junk.models(); i++) {
            String ns = pickNamespace();
            Placed p = place(ns, "models", ".json");
            // The first models each use one hybrid texture on their first part, so every real-looking texture is referenced.
            TextureRef anchor = i < hybrids.size() ? hybrids.get(i) : null;
            Draft draft = uniqueDraft(() -> buildDraft(ns, anchor));
            out.put(p.zipPath(), draft.model());
            out.putAll(draft.textures());
            partTextures += draft.textures().size();
        }
        int sounds = addSounds(junk.sounds());
        int textureTotal = junk.textures() + partTextures;
        return new Result(out, junk.models(), textureTotal, sounds, 0, warnings);
    }

    private int addSounds(int count) {
        if (count == 0) {
            return 0;
        }
        if (ffmpeg.isEmpty()) {
            warnings.add("ffmpeg was not found on the PATH, so " + count + " junk sound(s) were skipped. "
                    + "Install ffmpeg to get junk sounds.");
            return 0;
        }
        int added = 0;
        for (int i = 0; i < count; i++) {
            Placed p = place(pickNamespace(), "sounds", ".ogg");
            try {
                byte[] ogg = unique(this::soundBytes);
                out.put(p.zipPath(), ogg);
                added++;
            } catch (UncheckedIOException e) {
                warnings.add("A junk sound could not be encoded and was skipped: " + e.getCause().getMessage());
                break;
            }
        }
        return added;
    }

    private String pickNamespace() {
        return namespaces.get(rnd.nextInt(namespaces.size()));
    }

    /**
     * A free path for one junk file, directly in its type folder (assets/ns/textures or assets/ns/models).
     * Junk never goes into subfolders, so it sits next to the pack's own files.
     */
    private Placed place(String ns, String typeFolder, String ext) {
        while (true) {
            String relative = hex();
            String zip = "assets/" + ns + "/" + typeFolder + "/" + relative + ext;
            if (!existing.contains(zip) && !out.containsKey(zip) && !reserved.contains(zip)) {
                return new Placed(ns, relative, zip);
            }
        }
    }

    /**
     * Calls the generator until it makes content nobody else in the junk has. Returns the first new result.
     */
    private byte[] unique(Supplier<byte[]> generator) {
        byte[] content = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            content = generator.get();
            if (seen.add(sha256(content))) {
                return content;
            }
        }
        return content; // practically unreachable; the content is still valid, just not guaranteed unique
    }

    private byte[] soundBytes() {
        try {
            return VorbisEncoder.encode(ffmpeg.orElseThrow(), JunkAudio.synthesize(rnd));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("interrupted while encoding junk audio", e));
        }
    }

    /**
     * A random texture: one of several patterns, with its own palette. Some palette entries are
     * transparent, like real item sprites.
     */
    /** A random texture: one of several patterns, with its own palette. */
    private byte[] png(int size) {
        return encode(noiseImage(size));
    }

    /**
     * Noise, with one rectangle copied from a real pack texture. The copied pixels are the real ones,
     * so only that part looks like the pack's art. The rectangle is written to out[0].
     */
    private byte[] pngWithRealPixels(int size, Rect[] out) {
        BufferedImage img = noiseImage(size);
        BufferedImage real = realImages.get(rnd.nextInt(realImages.size()));
        int w = Math.min(Math.min(real.getWidth(), size), 4 + rnd.nextInt(Math.max(1, size / 2)));
        int h = Math.min(Math.min(real.getHeight(), size), 4 + rnd.nextInt(Math.max(1, size / 2)));
        int sx = rnd.nextInt(real.getWidth() - w + 1);
        int sy = rnd.nextInt(real.getHeight() - h + 1);
        int dx = rnd.nextInt(size - w + 1);
        int dy = rnd.nextInt(size - h + 1);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(dx + x, dy + y, real.getRGB(sx + x, sy + y));
            }
        }
        out[0] = new Rect(dx, dy, dx + w, dy + h);
        return encode(img);
    }

    private BufferedImage noiseImage(int size) {
        int colours = 2 + rnd.nextInt(4);
        int[] palette = new int[colours];
        for (int i = 0; i < colours; i++) {
            int alpha = (i == 0 && rnd.nextInt(3) == 0) ? 0 : 0xFF;
            palette[i] = (alpha << 24) | rnd.nextInt(0x1000000);
        }
        int pattern = rnd.nextInt(4);
        int period = 1 + rnd.nextInt(4);
        int cx = rnd.nextInt(size);
        int cy = rnd.nextInt(size);
        int ring = 2 + rnd.nextInt(6);
        boolean vertical = rnd.nextBoolean();

        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = switch (pattern) {
                    case 0 -> rnd.nextInt(colours);                                       // noise
                    case 1 -> ((vertical ? x : y) / period) % colours;                    // stripes
                    case 2 -> ((x / period) + (y / period)) % colours;                    // checker
                    default -> (((x - cx) * (x - cx) + (y - cy) * (y - cy)) / ring) % colours; // rings
                };
                img.setRGB(x, y, palette[index]);
            }
        }
        return img;
    }

    private static byte[] encode(BufferedImage img) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Decodes up to MAX_REAL_TEXTURES of the pack's own PNGs that are big enough to copy from. */
    private static List<BufferedImage> decodeRealTextures(Map<String, byte[]> files) {
        List<BufferedImage> out = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            if (out.size() >= MAX_REAL_TEXTURES) {
                break;
            }
            if (!e.getKey().startsWith("assets/") || !e.getKey().endsWith(".png")) {
                continue;
            }
            try {
                BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(e.getValue()));
                if (img != null && img.getWidth() >= 4 && img.getHeight() >= 4) {
                    out.add(img);
                }
            } catch (IOException ignored) {
                // A texture that cannot be decoded is simply not used as a source.
            }
        }
        return out;
    }

    /**
     * Every model part (cube) gets its own PNG, so no two parts of a junk model share a texture.
     * The first part can reuse an anchor texture from the pool, which is how real-pixel textures get referenced.
     */
    private Draft buildDraft(String ns, TextureRef anchor) {
        Map<String, byte[]> pngs = new LinkedHashMap<>();
        List<TextureRef> parts = new ArrayList<>();
        int partCount = 1 + rnd.nextInt(6);
        for (int c = 0; c < partCount; c++) {
            if (c == 0 && anchor != null) {
                parts.add(anchor);
                continue;
            }
            int size = TEXTURE_SIZES[rnd.nextInt(TEXTURE_SIZES.length)];
            Placed p = place(ns, "textures", ".png");
            boolean real = !realImages.isEmpty() && rnd.nextInt(3) == 0;
            Rect[] region = new Rect[1];
            byte[] png = real ? pngWithRealPixels(size, region) : png(size);
            String path = p.zipPath();
            pngs.put(path, png);
            reserved.add(path);
            parts.add(new TextureRef(ns + ":" + p.relative(), size, real ? region[0] : null));
        }

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("parent", PARENTS[rnd.nextInt(PARENTS.length)]);
        model.put("ambientocclusion", rnd.nextBoolean());
        Map<String, Object> texMap = new LinkedHashMap<>();
        for (int c = 0; c < parts.size(); c++) {
            texMap.put("layer" + c, parts.get(c).ref());
        }
        model.put("textures", texMap);

        Map<String, Object> display = new LinkedHashMap<>();
        Map<String, Object> gui = new LinkedHashMap<>();
        gui.put("rotation", List.of(rnd.nextInt(360), rnd.nextInt(360), rnd.nextInt(360)));
        gui.put("scale", List.of(0.5 + rnd.nextInt(10) / 10.0, 0.5 + rnd.nextInt(10) / 10.0, 0.5 + rnd.nextInt(10) / 10.0));
        display.put("gui", gui);
        model.put("display", display);

        List<Object> elements = new ArrayList<>();
        for (int c = 0; c < parts.size(); c++) {
            elements.add(cube(c, parts.get(c), anchor != null && c == 0));
        }
        model.put("elements", elements);
        return new Draft(PackSource.utf8(Json.write(model, !minify)), pngs);
    }

    /** Draws drafts until one has a model and PNGs nobody else in the junk has. */
    private Draft uniqueDraft(Supplier<Draft> generator) {
        Draft draft = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            draft = generator.get();
            List<String> hashes = new ArrayList<>();
            hashes.add(sha256(draft.model()));
            for (byte[] png : draft.textures().values()) {
                hashes.add(sha256(png));
            }
            if (new HashSet<>(hashes).size() == hashes.size() && hashes.stream().noneMatch(seen::contains)) {
                seen.addAll(hashes);
                return draft;
            }
        }
        return draft; // practically unreachable
    }

    /** One cube. Its faces all use this part's own texture, layer{index}. */
    private Map<String, Object> cube(int index, TextureRef part, boolean anchored) {
        int size = part.size();
        int x = rnd.nextInt(size);
        int y = rnd.nextInt(size);
        int z = rnd.nextInt(size);
        Map<String, Object> cube = new LinkedHashMap<>();
        cube.put("name", hex());
        cube.put("from", List.of(x, y, z));
        cube.put("to", List.of(x + 1 + rnd.nextInt(size / 2), y + 1 + rnd.nextInt(size / 2), z + 1 + rnd.nextInt(size / 2)));
        if (rnd.nextBoolean()) {
            Map<String, Object> rotation = new LinkedHashMap<>();
            rotation.put("axis", String.valueOf("xyz".charAt(rnd.nextInt(3))));
            rotation.put("angle", 22 * (rnd.nextInt(4) + 1));
            rotation.put("origin", List.of(size / 2, size / 2, size / 2));
            cube.put("rotation", rotation);
        }
        Map<String, Object> faces = new LinkedHashMap<>();
        int faceCount = 2 + rnd.nextInt(FACES.length - 1);
        List<String> chosen = new ArrayList<>(List.of(FACES));
        java.util.Collections.shuffle(chosen, rnd);
        for (int idx = 0; idx < faceCount; idx++) {
            Map<String, Object> f = new LinkedHashMap<>();
            boolean first = anchored && idx == 0;
            if (part.region() != null && (first || rnd.nextBoolean())) {
                Rect r = part.region();
                f.put("uv", List.of(r.x1(), r.y1(), r.x2(), r.y2()));
            } else {
                addRandomUv(f, size);
            }
            f.put("texture", "#layer" + index);
            if (rnd.nextInt(3) == 0) {
                f.put("rotation", 90 * rnd.nextInt(4));
            }
            faces.put(chosen.get(idx), f);
        }
        cube.put("faces", faces);
        return cube;
    }

    private void addRandomUv(Map<String, Object> face, int size) {
        int u1 = rnd.nextInt(size);
        int v1 = rnd.nextInt(size);
        face.put("uv", List.of(u1, v1, u1 + 1 + rnd.nextInt(size - u1), v1 + 1 + rnd.nextInt(size - v1)));
    }

    /** Eight random hex characters. */
    private String hex() {
        return String.format("%08x", rnd.nextInt());
    }

    private static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
