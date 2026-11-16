package me.amjadsmarzug.packobfscurer;

import me.amjadsmarzug.packobfscurer.core.ObfuscationResult;
import me.amjadsmarzug.packobfscurer.core.PackBatch;
import me.amjadsmarzug.packobfscurer.core.PackObfuscator;
import me.amjadsmarzug.packobfscurer.core.PackOptions;
import me.amjadsmarzug.packobfscurer.core.PackRestorer;
import me.amjadsmarzug.packobfscurer.core.PackSource;
import me.amjadsmarzug.packobfscurer.core.PackVerifier;
import me.amjadsmarzug.packobfscurer.ui.MainWindow;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

/**
 * Entry point. With no arguments the GUI opens. Otherwise the command line runs:
 *
 * <pre>
 *   PackObfuscator &lt;pack folder or .zip&gt; [output.zip] [options]
 *   PackObfuscator --batch &lt;folder&gt; [--out &lt;folder&gt;] [options]
 *   PackObfuscator --verify &lt;pack folder or .zip&gt;
 *   PackObfuscator --restore &lt;obfuscated.zip&gt; --mapping &lt;mapping.json&gt; [--out &lt;output.zip&gt;]
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            SwingUtilities.invokeLater(MainWindow::open);
            return;
        }
        if (args[0].equals("--help") || args[0].equals("-h")) {
            System.out.println(USAGE);
            return;
        }
        System.exit(runCli(args));
    }

    static final String USAGE = """
            PackObfuscator - obfuscates Minecraft resource packs.

            GUI:  run with no arguments.

            Obfuscate:  PackObfuscator <pack folder or .zip> [output.zip] [options]
            Batch:      PackObfuscator --batch <folder> [--out <folder>] [options]
            Verify:     PackObfuscator --verify <pack folder or .zip>
            Restore:    PackObfuscator --restore <obfuscated.zip> --mapping <mapping.json> [--out <output.zip>]

            Presets:  --preset light | standard | max   (default: standard)

            Options (each one turns a step on or off, after the preset):
              --rename-namespaces / --keep-namespaces
              --keep-cube-names        keep cube (element) names inside models
              --keep-groups            keep the groups tree inside models
              --no-models | --no-textures | --no-sounds
              --no-minify              keep JSON formatted
              --keep-junk              do not remove editor, git and OS leftovers
              --keep-metadata          do not strip credits, PNG/Vorbis metadata, or shuffle order
              --split-textures         give every model part its own PNG (default: on in Standard and Max)
              --keep-textures          keep models using their original textures
              --no-mapping             do not write the name map
              --seed N                 reproducible names
              --description TEXT       replace the pack description
              --protect PREFIX         also keep names under an asset folder (repeatable)
              --junk-models N          decoy models (default by preset)
              --junk-textures N        decoy PNG textures
              --junk-sounds N          decoy sounds (needs ffmpeg)
              --no-junk                no decoy files
            """;

    /** Runs the command line with the given arguments and returns the exit code. Used by the CLI-only launcher. */
    public static int cli(String[] args) {
        if (args.length == 0) {
            System.out.println(USAGE);
            return 2;
        }
        return runCli(args);
    }

    static int runCli(String[] args) {
        try {
            return dispatch(args);
        } catch (Exception e) {
            System.err.println("Error: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
            return 1;
        }
    }

    private static int dispatch(String[] args) throws Exception {
        switch (args[0]) {
            case "--verify":
                return verify(args);
            case "--restore":
                return restore(args);
            case "--batch":
                return batch(args);
            case "--check-update":
                return update(false);
            case "--update":
                return update(true);
            default:
                return obfuscate(args);
        }
    }

    /** Presets come first, so later flags can override them whatever order they are given in. */
    private static PackOptions parseOptions(List<String> args, List<String> positional, String[] extra) {
        PackOptions.Preset preset = PackOptions.Preset.STANDARD;
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals("--preset")) {
                preset = PackOptions.Preset.fromName(args.get(++i));
            }
        }
        PackOptions.Builder b = PackOptions.forPreset(preset).toBuilder();
        List<String> protectedExtra = new ArrayList<>();
        PackOptions.Junk junk = b.build().junk();
        int i = 0;
        while (i < args.size()) {
            String a = args.get(i++);
            switch (a) {
                case "--preset" -> i++;
                case "--rename-namespaces" -> b.renameNamespaces(true);
                case "--keep-namespaces" -> b.renameNamespaces(false);
                case "--keep-cube-names" -> b.renameCubes(false);
                case "--keep-groups" -> b.removeGroups(false);
                case "--no-models" -> b.renameModels(false);
                case "--no-textures" -> b.renameTextures(false);
                case "--no-sounds" -> b.renameSounds(false);
                case "--no-minify" -> b.minifyJson(false);
                case "--keep-junk" -> b.stripJunk(false);
                case "--keep-metadata" -> b.stripMetadata(false);
                case "--split-textures" -> b.splitTextures(true);
                case "--keep-textures" -> b.splitTextures(false);
                case "--no-mapping" -> b.writeMapping(false);
                case "--seed" -> b.seed(Long.parseLong(args.get(i++)));
                case "--description" -> b.descriptionOverride(args.get(i++));
                case "--protect" -> protectedExtra.add(args.get(i++));
                case "--junk-models" -> junk = new PackOptions.Junk(Integer.parseInt(args.get(i++)), junk.textures(), junk.sounds());
                case "--junk-textures" -> junk = new PackOptions.Junk(junk.models(), Integer.parseInt(args.get(i++)), junk.sounds());
                case "--junk-sounds" -> junk = new PackOptions.Junk(junk.models(), junk.textures(), Integer.parseInt(args.get(i++)));
                case "--no-junk" -> junk = PackOptions.Junk.NONE;
                default -> {
                    if (a.startsWith("--")) {
                        throw new IllegalArgumentException("Unknown option: " + a);
                    }
                    positional.add(a);
                }
            }
        }
        List<String> prefixes = new ArrayList<>(PackOptions.DEFAULT_PROTECTED);
        for (String p : protectedExtra) {
            prefixes.add(p.endsWith("/") ? p : p + "/");
        }
        return b.protectedPrefixes(prefixes).junk(junk).build();
    }

    private static int obfuscate(String[] args) throws Exception {
        List<String> positional = new ArrayList<>();
        PackOptions opts = parseOptions(List.of(args), positional, null);
        if (positional.isEmpty() || positional.size() > 2) {
            System.err.println(USAGE);
            return 2;
        }
        Path input = Path.of(positional.get(0));
        Path output = positional.size() == 2 ? Path.of(positional.get(1)) : PackObfuscator.defaultOutput(input);
        ObfuscationResult r = PackObfuscator.run(input, output, opts, System.out::println);
        System.out.println("Done in " + r.durationMillis() + " ms. Output: " + r.output());
        printWarnings(r.warnings());
        return 0;
    }

    private static int batch(String[] args) throws Exception {
        List<String> positional = new ArrayList<>();
        Path out = null;
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--out")) {
                out = Path.of(args[++i]);
            } else {
                rest.add(args[i]);
            }
        }
        if (rest.size() < 2) {
            System.err.println("Usage: PackObfuscator --batch <folder> [--out <folder>] [options]");
            return 2;
        }
        Path dir = Path.of(rest.get(1));
        PackOptions opts = parseOptions(rest.subList(2, rest.size()), positional, null);
        if (!positional.isEmpty()) {
            throw new IllegalArgumentException("Unexpected argument: " + positional.get(0));
        }
        Path outDir = out == null ? dir.resolve("obfuscated") : out;
        List<PackBatch.Item> items = PackBatch.run(dir, outDir, opts, System.out::println);
        boolean anyFailed = items.stream().anyMatch(i -> i.error() != null);
        return anyFailed ? 1 : 0;
    }

    private static int verify(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: PackObfuscator --verify <pack folder or .zip>");
            return 2;
        }
        SortedMap<String, byte[]> files = PackSource.read(Path.of(args[1]));
        List<String> problems = PackVerifier.verify(files);
        if (problems.isEmpty()) {
            System.out.println("OK: no problems found in " + args[1]);
            return 0;
        }
        for (String p : problems) {
            System.out.println("problem: " + p);
        }
        System.out.println(problems.size() + " problem(s) found.");
        return 1;
    }

    private static int restore(String[] args) throws Exception {
        Path input = null;
        Path mappingPath = null;
        Path out = null;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--mapping" -> mappingPath = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                default -> input = Path.of(args[i]);
            }
        }
        if (input == null || mappingPath == null) {
            System.err.println("Usage: PackObfuscator --restore <obfuscated.zip> --mapping <mapping.json> [--out <output.zip>]");
            return 2;
        }
        if (out == null) {
            String name = input.getFileName().toString().replaceFirst("(?i)-obfuscated\\.zip$", "");
            out = input.resolveSibling(name + "-restored.zip");
        }
        PackRestorer.Result r = PackRestorer.restore(PackSource.read(input), Files.readString(mappingPath));
        PackSource.writeZip(out, r.files());
        System.out.println("Restored " + r.restoredFiles() + " file name(s), removed " + r.removedJunk()
                + " junk file(s). Output: " + out);
        System.out.println("Not restored: " + String.join(", ", r.notes()) + ".");
        return 0;
    }

    /** Checks GitHub for a newer release. With install=true, downloads and starts the installer for this system. */
    private static int update(boolean install) throws Exception {
        String token = me.amjadsmarzug.packobfscurer.update.UpdateChecker.tokenFromEnvironment();
        var service = new me.amjadsmarzug.packobfscurer.update.UpdateService(
                me.amjadsmarzug.packobfscurer.update.UpdateService.currentVersion(),
                me.amjadsmarzug.packobfscurer.update.UpdateChecker.REPO, token);
        var status = service.check();
        if (status.latest().isEmpty()) {
            System.out.println("No release found. If the repository is private, set PACKOBF_UPDATE_TOKEN to a token that can read it.");
            return 1;
        }
        if (!status.updateAvailable()) {
            System.out.println("Up to date (version " + status.current() + ").");
            return 0;
        }
        var release = status.latest().orElseThrow();
        System.out.println("Version " + release.version() + " is available (you have " + status.current() + "): "
                + release.pageUrl());
        if (!install) {
            System.out.println("Run with --update to download and install it.");
            return 0;
        }
        var kind = me.amjadsmarzug.packobfscurer.update.Installers.detect();
        var file = service.download(release, kind, System.out::println);
        me.amjadsmarzug.packobfscurer.update.Installers.start(file, kind);
        System.out.println("Installer started.");
        return 0;
    }

    private static void printWarnings(List<String> warnings) {
        for (String w : warnings) {
            System.out.println("warning: " + w);
        }
    }
}
