package me.amjadsmarzug.packobfscurer.update;

import java.util.Arrays;
import java.util.Optional;

/** A release version such as "2.1.0", compared number by number. A leading "v" is ignored. */
public record Version(int[] parts) implements Comparable<Version> {

    public static Optional<Version> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String t = text.trim();
        if (t.startsWith("v") || t.startsWith("V")) {
            t = t.substring(1);
        }
        String[] pieces = t.split("\\.");
        int[] parts = new int[pieces.length];
        try {
            for (int i = 0; i < pieces.length; i++) {
                parts[i] = Integer.parseInt(pieces[i].replaceAll("[^0-9].*$", ""));
            }
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return pieces.length == 0 || t.isEmpty() ? Optional.empty() : Optional.of(new Version(parts));
    }

    @Override
    public int compareTo(Version other) {
        int n = Math.max(parts.length, other.parts.length);
        for (int i = 0; i < n; i++) {
            int a = i < parts.length ? parts[i] : 0;
            int b = i < other.parts.length ? other.parts[i] : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return Arrays.stream(parts).mapToObj(String::valueOf).reduce((a, b) -> a + "." + b).orElse("");
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Version v && compareTo(v) == 0;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
