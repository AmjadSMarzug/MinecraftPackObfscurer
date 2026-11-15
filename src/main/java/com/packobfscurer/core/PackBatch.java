package com.packobfscurer.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Obfuscates every pack in a folder with one set of options. One failing pack does not stop the others. */
public final class PackBatch {

    /** The outcome for one pack. Exactly one of result and error is set. */
    public record Item(Path input, Path output, ObfuscationResult result, String error) {
    }

    private PackBatch() {
    }

    /** Packs directly inside dir: folders containing pack.mcmeta, and .zip files that are not earlier outputs. */
    public static List<Path> findPacks(Path dir) throws IOException {
        List<Path> packs = new ArrayList<>();
        try (Stream<Path> children = Files.list(dir)) {
            children.sorted().forEach(p -> {
                String name = p.getFileName().toString();
                if (Files.isDirectory(p) && Files.isRegularFile(p.resolve("pack.mcmeta"))) {
                    packs.add(p);
                } else if (Files.isRegularFile(p) && name.toLowerCase().endsWith(".zip")
                        && !name.toLowerCase().endsWith("-obfuscated.zip")) {
                    packs.add(p);
                }
            });
        }
        return packs;
    }

    public static List<Item> run(Path dir, Path outDir, PackOptions opts, Consumer<String> log) throws IOException {
        Consumer<String> say = log == null ? s -> { } : log;
        List<Path> packs = findPacks(dir);
        if (packs.isEmpty()) {
            throw new IOException("No packs found in " + dir + ". Each pack must be a folder with pack.mcmeta, or a .zip.");
        }
        Files.createDirectories(outDir);
        say.accept("Batch: " + packs.size() + " pack(s) into " + outDir);
        List<Item> items = new ArrayList<>();
        for (Path pack : packs) {
            Path output = outDir.resolve(PackObfuscator.defaultOutput(pack).getFileName());
            say.accept("--- " + pack.getFileName());
            try {
                ObfuscationResult r = PackObfuscator.run(pack, output, opts, say);
                items.add(new Item(pack, output, r, null));
            } catch (IOException | RuntimeException e) {
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                say.accept("FAILED: " + message);
                items.add(new Item(pack, output, null, message));
            }
        }
        long failed = items.stream().filter(i -> i.error() != null).count();
        say.accept("Batch done: " + (items.size() - failed) + " succeeded, " + failed + " failed.");
        return items;
    }
}
