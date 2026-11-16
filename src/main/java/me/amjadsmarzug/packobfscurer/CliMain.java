package me.amjadsmarzug.packobfscurer;

/**
 * Entry point for the CLI-only build. Never opens the window, so it works on machines without a display.
 * Run as: java -cp PackObfuscator.jar me.amjadsmarzug.packobfscurer.CliMain [options]
 */
public final class CliMain {

    private CliMain() {
    }

    public static void main(String[] args) {
        System.exit(Main.cli(args));
    }
}
