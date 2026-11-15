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
    private static final int SUBFOLDERS_PER_NAMESPACE = 4;
    private static final int MAX_ATTEMPTS = 200;
    private static final String[] PARENTS = {"minecraft:item/generated", "minecraft:item/handheld"};
    private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    /** Where a junk file lives: namespace, plus the path inside its type folder without extension. */
    private record Placed(String namespace, String relative) {
    }

    /** A junk texture other junk models may point at. */
    private record TextureRef(String ref, int size) {
    }

    record Result(SortedMap<String, byte[]> files, int models, int textures, int sounds, int folders, List<String> warnings) {
    }

    private final Random rnd;
    private final Set<String> existing;
    private final boolean minify;
    private final Optional<Path> ffmpeg;
    private final List<String> namespaces;
    private final SortedMap<String, byte[]> out = new TreeMap<>();
    private final Set<String> dirs = new TreeSet<>();
    private final Map<String, String> roots = new HashMap<>();
    private final Map<String, List<String>> subfolders = new HashMap<>();
    private final List<TextureRef> textures = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();
    private final List<String> warnings = new ArrayList<>();

    private JunkGenerator(Set<AssetKey> owned, Set<String> existing, Random rnd, boolean minify, Optional<Path> ffmpeg) {
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
    static Result generate(PackOptions.Junk junk, Set<AssetKey> owned, Set<String> existing,
                           Random rnd, boolean minify, Optional<Path> ffmpeg) {
        return new JunkGenerator(owned, existing, rnd, minify, ffmpeg).run(junk);
    }

    private Result run(PackOptions.Junk junk) {
        if (junk.total() == 0) {
            return new Result(out, 0, 0, 0, 0, warnings);
        }
        if (namespaces.isEmpty()) {
            namespaces.add("minecraft");
        }
        for (String ns : namespaces) {
            roots.put(ns, hex());
            List<String> subs = new ArrayList<>();
            for (int i = 0; i < SUBFOLDERS_PER_NAMESPACE; i++) {
                subs.add(hex().substring(0, 4));
            }
            subfolders.put(ns, subs);
        }

        // Textures first, so junk models have something to point at.
        for (int i = 0; i < junk.textures(); i++) {
            int size = TEXTURE_SIZES[rnd.nextInt(TEXTURE_SIZES.length)];
            Placed p = place(pickNamespace(), "textures", ".png");
            out.put("assets/" + p.namespace() + "/textures/" + p.relative() + ".png",
                    unique(() -> png(size)));
            textures.add(new TextureRef(p.namespace() + ":" + p.relative(), size));
        }
        for (int i = 0; i < junk.models(); i++) {
            Placed p = place(pickNamespace(), "models", ".json");
            out.put("assets/" + p.namespace() + "/models/" + p.relative() + ".json",
                    unique(this::modelBytes));
        }
        int sounds = addSounds(junk.sounds());

        addEmptyFolders(Math.max(1, junk.total() / 8));

        // Every junk folder becomes a real zip directory entry, so empty folders survive the zip.
        for (String dir : dirs) {
            out.putIfAbsent(dir, new byte[0]);
        }
        return new Result(out, junk.models(), junk.textures(), sounds, dirs.size(), warnings);
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
                out.put("assets/" + p.namespace() + "/sounds/" + p.relative() + ".ogg", ogg);
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

    /** A new path under a junk root, unused in the pack and in the output so far. */
    private Placed place(String ns, String typeFolder, String ext) {
        List<String> subs = subfolders.get(ns);
        while (true) {
            String sub = subs.get(rnd.nextInt(subs.size()));
            String relative = roots.get(ns) + "/" + sub + "/" + hex();
            String path = "assets/" + ns + "/" + typeFolder + "/" + relative + ext;
            if (!existing.contains(path) && !out.containsKey(path)) {
                addDirs("assets/" + ns + "/" + typeFolder + "/", relative);
                return new Placed(ns, relative);
            }
        }
    }

    private void addDirs(String base, String relative) {
        String[] parts = relative.split("/");
        StringBuilder sb = new StringBuilder(base);
        for (int i = 0; i < parts.length - 1; i++) {
            sb.append(parts[i]).append('/');
            dirs.add(sb.toString());
        }
    }

    private void addEmptyFolders(int count) {
        String[] types = {"textures", "models", "sounds"};
        for (int i = 0; i < count; i++) {
            String ns = pickNamespace();
            String type = types[rnd.nextInt(types.length)];
            String dir = "assets/" + ns + "/" + type + "/" + roots.get(ns) + "/" + hex() + "/";
            if (!existing.contains(dir) && !out.containsKey(dir)) {
                dirs.add(dir);
                dirs.add("assets/" + ns + "/" + type + "/" + roots.get(ns) + "/");
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
    private byte[] png(int size) {
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
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A Blockbench-style model with random geometry, faces and display settings. */
    private byte[] modelBytes() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("parent", PARENTS[rnd.nextInt(PARENTS.length)]);
        model.put("ambientocclusion", rnd.nextBoolean());

        TextureRef tex = textures.isEmpty() || rnd.nextInt(4) == 0 ? null : textures.get(rnd.nextInt(textures.size()));
        if (tex != null) {
            Map<String, Object> texMap = new LinkedHashMap<>();
            texMap.put("layer0", tex.ref());
            model.put("textures", texMap);
        }
        int size = tex == null ? 16 : tex.size();

        Map<String, Object> display = new LinkedHashMap<>();
        Map<String, Object> gui = new LinkedHashMap<>();
        gui.put("rotation", List.of(rnd.nextInt(360), rnd.nextInt(360), rnd.nextInt(360)));
        gui.put("scale", List.of(0.5 + rnd.nextInt(10) / 10.0, 0.5 + rnd.nextInt(10) / 10.0, 0.5 + rnd.nextInt(10) / 10.0));
        display.put("gui", gui);
        model.put("display", display);

        List<Object> elements = new ArrayList<>();
        int cubes = 1 + rnd.nextInt(6);
        for (int c = 0; c < cubes; c++) {
            elements.add(cube(size, tex != null));
        }
        model.put("elements", elements);
        return PackSource.utf8(Json.write(model, !minify));
    }

    private Map<String, Object> cube(int size, boolean textured) {
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
        for (String face : chosen.subList(0, faceCount)) {
            Map<String, Object> f = new LinkedHashMap<>();
            int u1 = rnd.nextInt(size);
            int v1 = rnd.nextInt(size);
            f.put("uv", List.of(u1, v1, u1 + 1 + rnd.nextInt(size - u1), v1 + 1 + rnd.nextInt(size - v1)));
            if (textured) {
                f.put("texture", "#layer0");
            }
            if (rnd.nextInt(3) == 0) {
                f.put("rotation", 90 * rnd.nextInt(4));
            }
            faces.put(face, f);
        }
        cube.put("faces", faces);
        return cube;
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
