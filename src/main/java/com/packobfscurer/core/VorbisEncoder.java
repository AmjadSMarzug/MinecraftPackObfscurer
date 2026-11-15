package com.packobfscurer.core;

import java.io.IOException;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Encodes PCM to Ogg Vorbis by calling ffmpeg. Java has no built-in Vorbis encoder, and the decoder
 * libraries available do not encode. ffmpeg is looked up on the PATH; if it is missing, junk sounds
 * are skipped with a warning and nothing else changes.
 */
final class VorbisEncoder {

    private static final String[] CODECS = {"libvorbis", "vorbis"};

    private VorbisEncoder() {
    }

    /** The ffmpeg executable on the PATH, if there is one. */
    static Optional<Path> locate() {
        String path = System.getenv("PATH");
        if (path == null) {
            return Optional.empty();
        }
        for (String dir : path.split(File.pathSeparator)) {
            for (String name : List.of("ffmpeg", "ffmpeg.exe")) {
                Path candidate = Path.of(dir, name);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    /** Encodes mono 16-bit PCM at {@link JunkAudio#SAMPLE_RATE} into an Ogg Vorbis file. */
    static byte[] encode(Path ffmpeg, short[] pcm) throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("packobfuscator-junk");
        try {
            Path raw = dir.resolve("in.raw");
            Path ogg = dir.resolve("out.ogg");
            ByteBuffer buf = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : pcm) {
                buf.putShort(s);
            }
            Files.write(raw, buf.array());

            String lastError = "";
            for (String codec : CODECS) {
                List<String> cmd = new java.util.ArrayList<>(List.of(
                        ffmpeg.toString(), "-hide_banner", "-loglevel", "error", "-y",
                        "-f", "s16le", "-ar", String.valueOf(JunkAudio.SAMPLE_RATE), "-ac", "1",
                        "-i", raw.toString(), "-c:a", codec, "-q:a", "3"));
                if (codec.equals("vorbis")) {
                    cmd.addAll(List.of("-strict", "experimental"));
                }
                cmd.add(ogg.toString());
                Process p = new ProcessBuilder(cmd).redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.PIPE).start();
                String output = new String(p.getInputStream().readAllBytes());
                if (!p.waitFor(60, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    throw new IOException("ffmpeg timed out while encoding junk audio");
                }
                if (p.exitValue() == 0 && Files.size(ogg) > 0) {
                    return Files.readAllBytes(ogg);
                }
                lastError = output.trim();
            }
            throw new IOException("ffmpeg could not encode Ogg Vorbis: " + lastError);
        } finally {
            deleteQuietly(dir);
        }
    }

    /**
     * Copies the audio stream of an Ogg file into a new Ogg file without its tags.
     * No re-encoding happens, so the sound quality is unchanged.
     */
    static byte[] remux(Path ffmpeg, byte[] ogg) throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("packobfuscator-remux");
        try {
            Path in = dir.resolve("in.ogg");
            Path out = dir.resolve("out.ogg");
            Files.write(in, ogg);
            Process p = new ProcessBuilder(ffmpeg.toString(), "-hide_banner", "-loglevel", "error", "-y",
                    "-i", in.toString(), "-map", "0:a", "-map_metadata", "-1", "-c:a", "copy", "-f", "ogg", out.toString())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.PIPE).start();
            String output = new String(p.getInputStream().readAllBytes());
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("ffmpeg timed out while removing tags");
            }
            if (p.exitValue() != 0 || Files.size(out) == 0) {
                throw new IOException("ffmpeg could not remove tags: " + output.trim());
            }
            return Files.readAllBytes(out);
        } finally {
            deleteQuietly(dir);
        }
    }

    private static void deleteQuietly(Path dir) {
        try (var files = Files.list(dir)) {
            files.forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // Temp files are small. A leftover folder is not worth failing the run.
        }
        dir.toFile().delete();
    }
}
