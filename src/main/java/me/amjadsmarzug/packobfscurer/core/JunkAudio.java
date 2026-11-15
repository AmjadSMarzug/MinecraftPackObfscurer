package me.amjadsmarzug.packobfscurer.core;

import java.util.Random;

/**
 * Makes random sound-effect-like audio for junk sounds. Every sound gets its own base pitch, pitch
 * glide, waveform, noise amount, envelope and length, so no two junk sounds sound alike.
 */
final class JunkAudio {

    static final int SAMPLE_RATE = 44100;

    private static final double MIN_SECONDS = 0.25;
    private static final double MAX_SECONDS = 4.0;
    private static final double MIN_PITCH_HZ = 110;
    private static final double PITCH_OCTAVES = 4.5;

    private JunkAudio() {
    }

    /** Mono 16-bit PCM at {@link #SAMPLE_RATE}. */
    static short[] synthesize(Random rnd) {
        double seconds = MIN_SECONDS + rnd.nextDouble() * (MAX_SECONDS - MIN_SECONDS);
        int n = (int) (seconds * SAMPLE_RATE);
        double basePitch = MIN_PITCH_HZ * Math.pow(2, rnd.nextDouble() * PITCH_OCTAVES);
        double glideOctaves = (rnd.nextDouble() - 0.5) * 1.6;
        int waveform = rnd.nextInt(4);
        double noise = rnd.nextDouble() * 0.35;
        double attack = 0.002 + rnd.nextDouble() * 0.15;
        double decay = 0.8 + rnd.nextDouble() * 4.0;
        double vibratoHz = rnd.nextDouble() * 12;
        double vibratoDepth = rnd.nextDouble() * 0.04;

        short[] pcm = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double progress = t / seconds;
            double vibrato = 1 + vibratoDepth * Math.sin(2 * Math.PI * vibratoHz * t);
            double freq = basePitch * Math.pow(2, glideOctaves * progress) * vibrato;
            phase += 2 * Math.PI * freq / SAMPLE_RATE;
            double tone = wave(waveform, phase);
            double hiss = rnd.nextDouble() * 2 - 1;
            double envelope = Math.min(1, t / attack) * Math.exp(-t * decay);
            double v = ((1 - noise) * tone + noise * hiss) * envelope * 0.6;
            pcm[i] = (short) Math.round(Math.max(-1, Math.min(1, v)) * Short.MAX_VALUE);
        }
        return pcm;
    }

    private static double wave(int kind, double phase) {
        double p = phase % (2 * Math.PI);
        if (p < 0) {
            p += 2 * Math.PI;
        }
        return switch (kind) {
            case 0 -> Math.sin(p);                                  // sine
            case 1 -> p / Math.PI - 1;                              // sawtooth
            case 2 -> p < Math.PI ? 1 : -1;                         // square
            default -> 1 - Math.abs(p / Math.PI - 1) * 2;           // triangle
        };
    }
}
