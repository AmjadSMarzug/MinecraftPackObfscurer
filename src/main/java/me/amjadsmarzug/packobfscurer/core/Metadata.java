package me.amjadsmarzug.packobfscurer.core;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Removes identifying metadata from pack files.
 *
 * PNG text chunks and timestamps are dropped by decoding and re-encoding the pixels. The pixels are
 * unchanged. Blockbench "credit" fields are removed from JSON. Vorbis tags are removed from sounds
 * by {@link VorbisEncoder#remux}, which copies the audio stream without re-encoding it.
 */
final class Metadata {

    private Metadata() {
    }

    /** Returns the PNG re-encoded without its ancillary chunks. Returns the input unchanged if it cannot be decoded. */
    static byte[] cleanPng(byte[] png) {
        try {
            BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (img == null) {
                return png;
            }
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                ImageIO.write(img, "png", bos);
                return bos.toByteArray();
            }
        } catch (IOException e) {
            return png;
        }
    }

    /** Removes the top-level "credit" field of a model. Other fields are untouched. */
    static Object stripCredits(Object doc) {
        if (!(doc instanceof Map<?, ?> map) || !map.containsKey("credit")) {
            return doc;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((k, v) -> {
            if (!"credit".equals(k)) {
                out.put(String.valueOf(k), v);
            }
        });
        return out;
    }
}
