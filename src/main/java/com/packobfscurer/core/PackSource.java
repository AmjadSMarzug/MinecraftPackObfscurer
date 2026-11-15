package com.packobfscurer.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Reads a resource pack from a folder or zip into memory, and writes the result back as a zip. */
public final class PackSource {

    private PackSource() {
    }

    /** Returns every file in the pack keyed by its forward-slash path, with the pack root at the top. */
    public static SortedMap<String, byte[]> read(Path input) throws IOException {
        SortedMap<String, byte[]> files = new TreeMap<>();
        if (Files.isDirectory(input)) {
            try (Stream<Path> walk = Files.walk(input)) {
                for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    String rel = input.relativize(p).toString().replace('\\', '/');
                    files.put(rel, Files.readAllBytes(p));
                }
            }
        } else if (Files.isRegularFile(input) && input.getFileName().toString().toLowerCase().endsWith(".zip")) {
            try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(input))) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (!e.isDirectory()) {
                        files.put(e.getName().replace('\\', '/'), zin.readAllBytes());
                    }
                }
            }
        } else {
            throw new IOException("Not a folder or .zip file: " + input);
        }
        return stripSingleWrapperFolder(files);
    }

    /** Zips often wrap the pack in one folder (MyPack/pack.mcmeta). Remove that wrapper. */
    static SortedMap<String, byte[]> stripSingleWrapperFolder(SortedMap<String, byte[]> files) {
        if (files.containsKey("pack.mcmeta")) {
            return files;
        }
        String prefix = null;
        for (String name : files.keySet()) {
            if (name.endsWith("/pack.mcmeta") && name.indexOf('/') == name.length() - "/pack.mcmeta".length()) {
                prefix = name.substring(0, name.length() - "pack.mcmeta".length());
                break;
            }
        }
        if (prefix == null) {
            return files;
        }
        SortedMap<String, byte[]> out = new TreeMap<>();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                out.put(e.getKey().substring(prefix.length()), e.getValue());
            }
        }
        return out;
    }

    /** Writes entries in sorted order with normal timestamps. */
    public static void writeZip(Path target, Map<String, byte[]> files) throws IOException {
        writeZip(target, files, new ArrayList<>(new TreeSet<>(files.keySet())), false);
    }

    /**
     * Writes the zip to a temp file first, then moves it into place, so a failed run never leaves a half zip.
     * Keys ending in "/" are written as empty folder entries.
     *
     * @param order      entry order; must list every key
     * @param fixedTimes give every entry the same timestamp (1980-01-01), so the zip reveals no build times
     */
    public static void writeZip(Path target, Map<String, byte[]> files, List<String> order, boolean fixedTimes) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        long fixed = ZonedDateTime.of(1980, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
        try (OutputStream os = Files.newOutputStream(tmp);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            for (String name : order) {
                ZipEntry entry = new ZipEntry(name);
                if (fixedTimes) {
                    entry.setTime(fixed);
                }
                zos.putNextEntry(entry);
                if (!name.endsWith("/")) {
                    zos.write(files.get(name));
                }
                zos.closeEntry();
            }
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
