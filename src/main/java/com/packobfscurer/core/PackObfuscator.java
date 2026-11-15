package com.packobfscurer.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The obfuscation pipeline for Minecraft resource packs.
 *
 * <ol>
 *   <li>read the pack (folder or zip) into memory</li>
 *   <li>strip junk files</li>
 *   <li>check that every JSON file parses</li>
 *   <li>choose new names for models, textures, sounds and (optionally) namespaces</li>
 *   <li>rewrite references, clean up cubes and groups, strip metadata, and minify</li>
 *   <li>add junk, verify the result, and write the zip and the mapping</li>
 * </ol>
 *
 * The input is never modified.
 */
public final class PackObfuscator {

    /** Mapping file format version. Version 2 records every moved file and every junk file, so a pack can be restored. */
    public static final int MAPPING_VERSION = 2;

    private static final Set<String> JUNK_SEGMENTS = Set.of(".git", ".svn", ".idea", ".vscode", ".github", "__MACOSX");
    private static final Set<String> JUNK_NAMES = Set.of(".DS_Store", "Thumbs.db", "desktop.ini", ".gitignore", ".gitattributes");
    private static final Set<String> JUNK_EXTENSIONS = Set.of(
            ".psd", ".xcf", ".kra", ".ase", ".aseprite", ".blend", ".bbmodel", ".md",
            ".zip", ".rar", ".7z", ".bak", ".tmp", ".log");
    /** Namespaces the game owns. They are never renamed. */
    static final Set<String> VANILLA_NAMESPACES = Set.of("minecraft", "realms");

    /** New names chosen for the pack's assets, and where each file moves to. */
    private record Renames(Map<AssetKey, AssetKey> keys, Map<String, String> moves) {
    }

    /** The rewritten files plus counters for the result. */
    private record Rewritten(SortedMap<String, byte[]> files, int minified, int refFiles, int cubesRenamed,
                             int groupsRemoved, Map<String, String> changedPaths) {
    }

    private PackObfuscator() {
    }

    public static ObfuscationResult run(Path input, Path output, PackOptions opts, Consumer<String> log) throws IOException {
        long started = System.currentTimeMillis();
        Consumer<String> say = log == null ? s -> { } : log;
        checkPaths(input, output);

        SortedMap<String, byte[]> files = PackSource.read(input);
        int inputFiles = files.size();
        long inputBytes = totalBytes(files);
        say.accept("Read " + inputFiles + " files (" + human(inputBytes) + ") from " + input.getFileName());
        if (!files.containsKey("pack.mcmeta")) {
            throw new IOException("This is not a resource pack: pack.mcmeta is missing at the root.");
        }

        int stripped = opts.stripJunk() ? stripJunk(files, say) : 0;
        Map<String, Object> docs = parseJson(files, say);
        Set<AssetKey> owned = ownedAssets(files);
        Random rnd = opts.seed() == null ? new SecureRandom() : new Random(opts.seed());
        Renames renames = chooseRenames(files, owned, opts, rnd, say);
        Map<String, String> nsMap = opts.renameNamespaces() ? chooseNamespaces(files, rnd) : Map.of();
        if (!nsMap.isEmpty()) {
            say.accept("Renamed " + nsMap.size() + " namespace(s): " + nsMap);
        }

        Optional<Path> ffmpeg = opts.stripMetadata() ? VorbisEncoder.locate() : Optional.empty();
        List<String> warnings = new ArrayList<>();
        if (opts.stripMetadata() && ffmpeg.isEmpty() && files.keySet().stream().anyMatch(k -> k.endsWith(".ogg"))) {
            warnings.add("ffmpeg was not found, so Vorbis tags in the sounds were kept. Install ffmpeg to remove them.");
        }

        Rewritten out = rewrite(files, docs, owned, renames, nsMap, opts, rnd, ffmpeg, warnings);
        say.accept("Updated references in " + out.refFiles() + " JSON files");
        if (opts.renameCubes() || opts.removeGroups()) {
            say.accept("Model cleanup: " + out.cubesRenamed() + " cube names changed, "
                    + out.groupsRemoved() + " groups removed");
        }

        Set<AssetKey> junkOwned = new HashSet<>();
        for (AssetKey k : owned) {
            junkOwned.add(new AssetKey(k.type(), nsMap.getOrDefault(k.namespace(), k.namespace()), k.path()));
        }
        JunkGenerator.Result junk = JunkGenerator.generate(opts.junk(), junkOwned,
                new HashSet<>(out.files().keySet()), rnd, opts.minifyJson(), VorbisEncoder.locate());
        out.files().putAll(junk.files());
        int junkFiles = junk.models() + junk.textures() + junk.sounds();
        if (opts.junk().total() > 0) {
            say.accept("Added " + junk.models() + " junk models, " + junk.textures() + " junk textures, "
                    + junk.sounds() + " junk sounds, " + junk.folders() + " junk folders");
        }
        warnings.addAll(junk.warnings());

        List<String> problems = PackVerifier.verify(out.files());
        for (String p : problems) {
            warnings.add("verify: " + p);
        }
        say.accept(problems.isEmpty() ? "Verified: no problems found"
                : problems.size() + " problem(s) found by verify. Check them before publishing.");

        List<String> order = new ArrayList<>(out.files().keySet());
        if (opts.stripMetadata()) {
            Collections.shuffle(order, rnd);
        }
        PackSource.writeZip(output, out.files(), order, opts.stripMetadata());
        long outputBytes = Files.size(output);
        say.accept("Wrote " + output + " (" + human(outputBytes) + ")");

        Path mappingFile = null;
        if (opts.writeMapping() && (!renames.keys().isEmpty() || !nsMap.isEmpty() || junkFiles > 0)) {
            mappingFile = mappingPath(output);
            Files.writeString(mappingFile,
                    mappingJson(renames.keys(), nsMap, out.changedPaths(), junk.files().keySet(), opts.seed()),
                    StandardCharsets.UTF_8);
            say.accept("Saved name map to " + mappingFile.getFileName());
        }

        int outputFiles = (int) out.files().keySet().stream().filter(k -> !k.endsWith("/")).count();
        return new ObfuscationResult(
                output.toAbsolutePath(), mappingFile, inputFiles, outputFiles, inputBytes, outputBytes,
                countType(renames.keys(), AssetKey.Type.MODEL),
                countType(renames.keys(), AssetKey.Type.TEXTURE),
                countType(renames.keys(), AssetKey.Type.SOUND),
                out.cubesRenamed(), out.groupsRemoved(), junkFiles, junk.folders(),
                stripped, out.minified(), warnings,
                System.currentTimeMillis() - started);
    }

    /** Default output next to the input: "MyPack" becomes "MyPack-obfuscated.zip" in the same folder. */
    public static Path defaultOutput(Path input) {
        Path abs = input.toAbsolutePath().normalize();
        String name = abs.getFileName().toString();
        if (name.toLowerCase().endsWith(".zip")) {
            name = name.substring(0, name.length() - 4);
        }
        Path parent = abs.getParent() == null ? abs : abs.getParent();
        return parent.resolve(name + "-obfuscated.zip");
    }

    public static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    // ------------------------------------------------------------------ steps

    private static void checkPaths(Path input, Path output) throws IOException {
        if (!Files.exists(input)) {
            throw new IOException("Input does not exist: " + input);
        }
        if (Files.exists(output) && Files.isSameFile(input, output)) {
            throw new IOException("The output file would overwrite the input. Choose a different output name.");
        }
    }

    private static long totalBytes(SortedMap<String, byte[]> files) {
        return files.values().stream().mapToLong(b -> b.length).sum();
    }

    private static int stripJunk(SortedMap<String, byte[]> files, Consumer<String> say) {
        int before = files.size();
        files.keySet().removeIf(PackObfuscator::isJunk);
        int stripped = before - files.size();
        say.accept("Removed " + stripped + " editor, git and OS leftover files");
        return stripped;
    }

    /** Parses every JSON file up front, so a broken file stops the run before anything is written. */
    private static Map<String, Object> parseJson(SortedMap<String, byte[]> files, Consumer<String> say) throws IOException {
        Map<String, Object> docs = new HashMap<>();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String path = e.getKey();
            if (!path.endsWith(".json") && !path.endsWith(".mcmeta")) {
                continue;
            }
            try {
                docs.put(path, Json.parse(new String(e.getValue(), StandardCharsets.UTF_8)));
            } catch (Json.ParseException ex) {
                throw new IOException("Invalid JSON in " + path + ": " + ex.getMessage(), ex);
            }
        }
        say.accept("Checked " + docs.size() + " JSON files, all valid");
        return docs;
    }

    private static Set<AssetKey> ownedAssets(SortedMap<String, byte[]> files) {
        Set<AssetKey> owned = new HashSet<>();
        for (String path : files.keySet()) {
            AssetKey.fromZipPath(path).ifPresent(owned::add);
        }
        return owned;
    }

    private static Renames chooseRenames(SortedMap<String, byte[]> files, Set<AssetKey> owned,
                                         PackOptions opts, Random rnd, Consumer<String> say) {
        Map<AssetKey, AssetKey> keys = new HashMap<>();
        Set<String> taken = new HashSet<>(files.keySet());
        int protectedCount = 0;
        List<AssetKey> sorted = owned.stream().sorted(Comparator.comparing(AssetKey::zipPath)).toList();
        for (AssetKey key : sorted) {
            if (!typeEnabled(key.type(), opts)) {
                continue;
            }
            if (isProtected(key, opts.protectedPrefixes())) {
                protectedCount++;
                continue;
            }
            AssetKey target = freshName(key, taken, rnd);
            taken.add(target.zipPath());
            taken.add(target.zipPath() + ".mcmeta");
            keys.put(key, target);
        }

        // A texture's .png.mcmeta moves with it.
        Map<String, String> moves = new HashMap<>();
        for (Map.Entry<AssetKey, AssetKey> r : keys.entrySet()) {
            String oldPath = r.getKey().zipPath();
            String newPath = r.getValue().zipPath();
            moves.put(oldPath, newPath);
            if (files.containsKey(oldPath + ".mcmeta")) {
                moves.put(oldPath + ".mcmeta", newPath + ".mcmeta");
            }
        }
        say.accept("Renamed " + countType(keys, AssetKey.Type.MODEL) + " models, "
                + countType(keys, AssetKey.Type.TEXTURE) + " textures, "
                + countType(keys, AssetKey.Type.SOUND) + " sounds ("
                + protectedCount + " kept for vanilla compatibility)");
        return new Renames(keys, moves);
    }

    /**
     * Gives each pack namespace a random name, such as "k3x9q" for "distant_destinies".
     * The vanilla namespaces are never renamed, and no new name matches an existing one.
     */
    static Map<String, String> chooseNamespaces(SortedMap<String, byte[]> files, Random rnd) {
        Set<String> packNamespaces = new HashSet<>();
        for (String path : files.keySet()) {
            AssetKey.fromZipPath(path).ifPresent(k -> packNamespaces.add(k.namespace()));
            if (path.startsWith("assets/")) {
                String[] parts = path.split("/", 3);
                if (parts.length >= 2) {
                    packNamespaces.add(parts[1]);
                }
            }
        }
        Set<String> reserved = new HashSet<>(packNamespaces);
        reserved.addAll(VANILLA_NAMESPACES);
        Map<String, String> map = new LinkedHashMap<>();
        for (String ns : new java.util.TreeSet<>(packNamespaces)) {
            if (VANILLA_NAMESPACES.contains(ns)) {
                continue;
            }
            String name;
            do {
                name = randomNamespace(rnd);
            } while (reserved.contains(name));
            reserved.add(name);
            map.put(ns, name);
        }
        return map;
    }

    private static String randomNamespace(Random rnd) {
        String first = "abcdefghijklmnopqrstuvwxyz";
        String rest = "abcdefghijklmnopqrstuvwxyz0123456789";
        int length = 4 + rnd.nextInt(4);
        StringBuilder sb = new StringBuilder().append(first.charAt(rnd.nextInt(first.length())));
        for (int i = 1; i < length; i++) {
            sb.append(rest.charAt(rnd.nextInt(rest.length())));
        }
        return sb.toString();
    }

    /** A random 8-character hex name in the same folder, unused anywhere in the pack. */
    private static AssetKey freshName(AssetKey key, Set<String> taken, Random rnd) {
        String dir = parentDir(key.path());
        while (true) {
            String hex = hex(rnd);
            AssetKey target = new AssetKey(key.type(), key.namespace(), dir.isEmpty() ? hex : dir + "/" + hex);
            if (!taken.contains(target.zipPath()) && !taken.contains(target.zipPath() + ".mcmeta")) {
                return target;
            }
        }
    }

    private static Rewritten rewrite(SortedMap<String, byte[]> files, Map<String, Object> docs, Set<AssetKey> owned,
                                     Renames renames, Map<String, String> nsMap, PackOptions opts, Random rnd,
                                     Optional<Path> ffmpeg, List<String> warnings) {
        UnaryOperator<AssetKey> resolver = k -> renames.keys().getOrDefault(k, k);
        Supplier<String> newName = () -> hex(rnd);
        boolean cleanModels = opts.renameCubes() || opts.removeGroups();
        // References are checked by the verifier on the output, so the rewriter's own misses are not reported twice.
        List<String> unresolved = new ArrayList<>();

        SortedMap<String, byte[]> out = new TreeMap<>();
        Map<String, String> changed = new LinkedHashMap<>();
        int minified = 0;
        int refFiles = 0;
        int cubes = 0;
        int groups = 0;
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String path = e.getKey();
            String newPath = NamespaceRewriter.movePath(renames.moves().getOrDefault(path, path), nsMap);
            if (!newPath.equals(path)) {
                changed.put(path, newPath);
            }
            Object doc = docs.get(path);
            if (doc == null) {
                out.put(newPath, binaryContent(path, e.getValue(), opts, ffmpeg, warnings));
                continue;
            }
            if (path.equals("pack.mcmeta") && opts.descriptionOverride() != null) {
                doc = withPackDescription(doc, opts.descriptionOverride());
            }
            boolean isAsset = path.startsWith("assets/");
            if (isAsset) {
                Object rewritten = new ReferenceRewriter(resolver, owned, path, unresolved).rewrite(doc);
                if (!doc.equals(rewritten)) {
                    refFiles++;
                }
                doc = rewritten;
            }
            doc = NamespaceRewriter.rewrite(doc, nsMap);
            boolean isModel = AssetKey.fromZipPath(path).filter(k -> k.type() == AssetKey.Type.MODEL).isPresent();
            if (isModel && cleanModels && doc instanceof Map<?, ?> model) {
                ModelCleaner.Clean clean = ModelCleaner.clean(model, newName, opts.renameCubes(), opts.removeGroups());
                doc = clean.doc();
                cubes += clean.cubesRenamed();
                groups += clean.groupsRemoved();
            }
            if (opts.stripMetadata()) {
                if (isAsset) {
                    doc = Metadata.stripCredits(doc);
                }
                doc = Json.shuffleKeys(doc, rnd);
            }
            if (opts.minifyJson()) {
                minified++;
            }
            out.put(newPath, PackSource.utf8(Json.write(doc, !opts.minifyJson())));
        }
        return new Rewritten(out, minified, refFiles, cubes, groups, changed);
    }

    /** PNG and Ogg files: strip metadata if asked. Everything else is copied unchanged. */
    private static byte[] binaryContent(String path, byte[] bytes, PackOptions opts, Optional<Path> ffmpeg, List<String> warnings) {
        if (!opts.stripMetadata() || !path.startsWith("assets/")) {
            return bytes;
        }
        if (path.endsWith(".png")) {
            return Metadata.cleanPng(bytes);
        }
        if (path.endsWith(".ogg") && ffmpeg.isPresent()) {
            try {
                return VorbisEncoder.remux(ffmpeg.get(), bytes);
            } catch (IOException e) {
                warnings.add(path + ": could not remove tags (" + e.getMessage() + "), kept as is");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                warnings.add(path + ": tag removal interrupted, kept as is");
            }
        }
        return bytes;
    }

    // ------------------------------------------------------------------ helpers

    private static String hex(Random rnd) {
        return String.format("%08x", rnd.nextInt());
    }

    static boolean isJunk(String path) {
        for (String seg : path.split("/")) {
            if (JUNK_SEGMENTS.contains(seg)) {
                return true;
            }
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (JUNK_NAMES.contains(name)) {
            return true;
        }
        String lower = name.toLowerCase();
        for (String ext : JUNK_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    static boolean isProtected(AssetKey key, List<String> prefixes) {
        String rel = key.assetRelative();
        for (String p : prefixes) {
            if (rel.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean typeEnabled(AssetKey.Type type, PackOptions opts) {
        return switch (type) {
            case MODEL -> opts.renameModels();
            case TEXTURE -> opts.renameTextures();
            case SOUND -> opts.renameSounds();
        };
    }

    private static int countType(Map<AssetKey, AssetKey> keys, AssetKey.Type type) {
        return (int) keys.keySet().stream().filter(k -> k.type() == type).count();
    }

    private static String parentDir(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    @SuppressWarnings("unchecked")
    private static Object withPackDescription(Object doc, String description) {
        if (!(doc instanceof Map<?, ?> root) || !(root.get("pack") instanceof Map<?, ?> pack)) {
            return doc;
        }
        Map<String, Object> packCopy = new LinkedHashMap<>((Map<String, Object>) pack);
        packCopy.put("description", description);
        Map<String, Object> rootCopy = new LinkedHashMap<>((Map<String, Object>) root);
        rootCopy.put("pack", packCopy);
        return rootCopy;
    }

    static Path mappingPath(Path output) {
        String name = output.getFileName().toString();
        if (name.toLowerCase().endsWith(".zip")) {
            name = name.substring(0, name.length() - 4);
        }
        return output.toAbsolutePath().resolveSibling(name + ".mapping.json");
    }

    /** The version 2 mapping: human-readable name maps, plus the exact file moves and junk list used to restore. */
    static String mappingJson(Map<AssetKey, AssetKey> renames, Map<String, String> nsMap,
                              Map<String, String> changedPaths, Set<String> junkPaths, Long seed) {
        Map<String, Object> models = new LinkedHashMap<>();
        Map<String, Object> textures = new LinkedHashMap<>();
        Map<String, Object> sounds = new LinkedHashMap<>();
        renames.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().zipPath()))
                .forEach(e -> {
                    AssetKey target = e.getValue();
                    AssetKey finalKey = new AssetKey(target.type(),
                            nsMap.getOrDefault(target.namespace(), target.namespace()), target.path());
                    Map<String, Object> bucket = switch (e.getKey().type()) {
                        case MODEL -> models;
                        case TEXTURE -> textures;
                        case SOUND -> sounds;
                    };
                    bucket.put(e.getKey().refId(), finalKey.refId());
                });
        Map<String, Object> files = new LinkedHashMap<>();
        changedPaths.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> files.put(e.getKey(), e.getValue()));
        Map<String, Object> namespaces = new LinkedHashMap<>(new java.util.TreeMap<>(nsMap));
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("format", "packobfuscator-mapping");
        doc.put("version", MAPPING_VERSION);
        doc.put("seed", seed);
        doc.put("namespaces", namespaces);
        doc.put("models", models);
        doc.put("textures", textures);
        doc.put("sounds", sounds);
        doc.put("files", files);
        doc.put("junk", new ArrayList<>(new java.util.TreeSet<>(junkPaths)));
        return Json.write(doc, true);
    }
}
