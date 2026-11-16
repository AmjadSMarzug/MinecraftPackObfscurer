# Pack Obfuscator

A desktop app and command-line tool that obfuscates Minecraft resource packs (texture packs).
It renames the pack's models, textures, sounds and (optionally) namespaces, rewrites every reference,
removes identifying metadata, adds decoy files, checks the result, and writes a new `.zip`.
Your original pack is never modified.

## Presets

| Preset | What it does |
| --- | --- |
| **Light** | Renames models, textures and sounds. Minifies JSON, strips junk and metadata. No cube cleanup, no namespaces, no decoys. |
| **Standard** (default) | Light, plus cube-name and group cleanup, and 25 models / 25 textures / 5 sounds of junk. |
| **Max** | Standard, plus namespace renaming, and 60 models / 60 textures / 12 sounds of junk. |

The GUI's dropdown switches presets. Changing any option by hand switches it to *Custom*.

## Using it

**GUI:** run the app with no arguments. Drop a pack folder or `.zip` onto the drop area, pick a preset,
then press **Obfuscate pack**. The output goes next to the input as `<name>-obfuscated.zip`.
**Batch folder...** runs every pack in a folder with the current options.

**Command line** (scripts and CI):

```
PackObfuscator <pack folder or .zip> [output.zip] [options]     obfuscate one pack
PackObfuscator --batch <folder> [--out <folder>] [options]      obfuscate every pack in a folder
PackObfuscator --verify <pack folder or .zip>                   check a pack for problems
PackObfuscator --restore <obfuscated.zip> --mapping <map.json> [--out <output.zip>]
```

Presets: `--preset light | standard | max` (default `standard`). Options after the preset override it:

```
--rename-namespaces / --keep-namespaces     --no-models | --no-textures | --no-sounds
--keep-cube-names   --keep-groups           --no-minify   --keep-junk   --keep-metadata
--no-mapping        --seed N                --description TEXT
--protect PREFIX    --junk-models N  --junk-textures N  --junk-sounds N  --no-junk
```

Exit code is 0 on success, 1 on failure, 2 for bad usage.

## What it does

1. **Strips junk**: `.git`, `.DS_Store`, `Thumbs.db`, `.psd`, `.bbmodel`, `.md` and similar files.
2. **Checks JSON**: every JSON file must parse. A broken file stops the run before anything is written.
3. **Renames** models, textures and sounds to random 8-character hex names. Folders are kept, and a texture's `.png.mcmeta` moves with it.
4. **Renames namespaces** (Max, or `--rename-namespaces`): each pack namespace such as `distant_destinies` gets a random name such as `nabivin`. Folders, every `ns:path` reference and every namespaced key are updated. `minecraft` and `realms` are never touched.
5. **Rewrites references** in models (`parent`, `textures`), item definitions (`model`), font providers (`file`), atlases (`resource`), particles, and `sounds.json`. Only references to files the pack contains are changed.
6. **Cleans models** (Standard and Max): replaces cube (element) names and group labels with random names, and deletes the `groups` tree. Cube geometry is unchanged.
7. **Strips metadata** (on by default): removes Blockbench `credit` fields, re-encodes PNGs without text chunks (the pixels are unchanged), removes Vorbis tags from sounds by stream copy (no re-encoding; needs ffmpeg), gives every zip entry the same timestamp, and shuffles zip entry order and JSON key order. Array order is never changed.
8. **Splits model textures** (Standard and Max, or `--split-textures`): every part (cube) of a real model gets its own PNG. The new PNG has the same size as the original, keeps only the pixels that part's faces sample, and fills every other pixel with junk noise. The rendered model is unchanged. This is checked against the original pixels for every face. Models the splitter cannot prove safe are left as they were: a model with a parent in the pack, a model used as another's parent, a face without UVs, a model without `texture_size` unless its texture is 16x16, or a texture also used by an atlas, font or particle, or one with animation metadata. The original texture is removed once nothing refers to it.
9. **Minifies** the JSON, unless turned off.
10. **Adds junk** (like junk classes in a Java obfuscator): decoy models, PNG textures, Ogg Vorbis sounds and empty folders that the game never loads. Every part of a junk model (each cube) has its own junk PNG, so no two parts of a model share a texture. About a third of those PNGs copy one real rectangle from the pack, so only that part looks like the pack's art. Every decoy is unique: textures get random patterns and palettes, models get random geometry, and sounds get their own pitch, length and envelope. Each file's content is hashed, so no two decoys match. Junk only points at other junk, and real files are byte-identical with junk on or off.
11. **Verifies** the result: invalid JSON, undecodable PNGs, non-Ogg sounds, and references to pack files that are missing. Problems are listed in the log and in the warnings.
12. **Saves a name map** (`<name>-obfuscated.mapping.json`). Keep it private: it is the key to the pack.

## Restoring a pack

`--restore` uses the version 2 mapping to undo the obfuscation: asset names, namespace names, every reference, and the junk files. It cannot restore everything, because the mapping does not record it:

- cube and group names, and deleted groups
- per-part texture splits (the original textures are replaced, not kept)
- removed credits and PNG/Vorbis metadata
- minified formatting (the output is pretty-printed)
- the pack description override

Keep the original pack if you need those back. Mappings from before version 2 can't restore a pack, because they have no file list.

## Requirements

- **ffmpeg** on the PATH, for two features: encoding junk sounds, and removing Vorbis tags from real sounds. Without it, both steps are skipped with a warning and everything else still runs. Java has no Vorbis encoder, so ffmpeg does the audio work.

Install it with the included script (it checks first, so it is safe to re-run):

```
scripts/install-ffmpeg.sh            # Linux (apt, dnf, pacman, zypper) and macOS (Homebrew)
scripts/install-ffmpeg.sh --check    # only check
```

On Windows, run `scripts\install-ffmpeg.ps1` (winget, Chocolatey or Scoop), then open a new terminal.

## Things to know before you release a pack

- **Protected folders.** Vanilla Minecraft loads some folders by their fixed names, so they are kept by default: `minecraft/textures/particle/`, `textures/gui/`, `textures/block/`, `models/block/`, `font/`, `lang/`, `atlases/`, and others. The list is editable in the app.
- **Vanilla paths.** If the pack replaces a vanilla model, texture or sound, that replacement only works if the pack also overrides everything that uses it. Test in game after obfuscating.
- **Verification is structural.** `--verify` and the automatic check cannot see vanilla assets without the client jar, so `minecraft:` references are not checked.
- **Junk and directory atlases.** A `directory` atlas loads every texture under its folder. Junk lives under random folders, so it only enters an atlas if you point one at the pack root. Check the atlas after junk is on, or set junk to 0.
- **Unsigned builds.** Windows SmartScreen and macOS Gatekeeper will warn about the app on first launch because it is not code-signed.
- **Free-text colons.** Namespace renaming changes any string that starts with a renamed namespace followed by `:`, including prose. This is rare in packs, and `--verify` will show it if a text string breaks.

## Updates

The app checks GitHub for a newer release once a day, in the background. When one exists, a bar appears
above the window with an **Update** button. The update asks first, downloads the installer for your system,
checks it against the published `SHA256SUMS.txt`, and only then starts it:

| System | What the update does |
| --- | --- |
| Linux (installed from the RPM) | Runs `rpm -Uvh` through `pkexec`, so you are asked for your password |
| Windows | Opens the new `.exe` installer for you to finish |
| macOS | Opens the new `.dmg`; drag the app into Applications |
| Linux portable zip, or any other install | Opens the release page. Automatic update is not possible here |

Command line: `PackObfuscator --check-update`, and `PackObfuscator --update` to download and install.

While the repository is private, the check needs a GitHub token that can read it, in the
`PACKOBF_UPDATE_TOKEN` environment variable. Without it, the check reports that no release was found.

## Credit and license

Every pack this tool makes carries the credit **"Obfuscated by AmjadSMarzug with PackObfuscator"**: in the
pack's description, in an `OBFUSCATED_BY.txt` file, and in the zip comment. The credit is always added and
cannot be turned off. Restoring a pack keeps the credit.

PackObfuscator is proprietary, with all rights reserved. See [LICENSE](LICENSE).

## Downloads

Each tagged release (`v1.2.3`) publishes:

| File | For |
| --- | --- |
| `PackObfuscator-<ver>-windows-portable.zip` | Windows, no install. Unzip and run `PackObfuscator.exe` |
| `PackObfuscator-<ver>-macos-portable.zip` | macOS, no install. Unzip and open `PackObfuscator.app` |
| `PackObfuscator-<ver>-linux-portable.zip` | Linux, no install. Unzip and run `PackObfuscator/bin/PackObfuscator` |
| `*.exe`, `*.dmg`, `*.deb` | Native installers |
| `SHA256SUMS.txt` | Checksums to verify the downloads |

Each tag `vX.Y.Z` publishes two releases:

- **`vX.Y.Z`**: the desktop app (the table above), with the CLI inside the portable archives.
- **`vX.Y.Z-cli`**: the command-line-only zip, `PackObfuscator-X.Y.Z-cli.zip`. It contains the jar and launchers
  for Linux, macOS and Windows (`packobfuscator` and `packobfuscator.cmd`). It needs Java 21 or newer on the PATH.
  Run it with no arguments to see the usage.

The portable builds include their own Java runtime, so Java does not need to be installed.

## Building

Requires JDK 21. Tested with Gradle 9.7 (the wrapper downloads it).

```
./gradlew test                       # unit tests
./gradlew jar                        # build/libs/PackObfuscator-<ver>.jar, runs with java -jar
./gradlew portableZip                # build/dist/*-portable.zip for this OS
./gradlew jpackageInstaller          # native installer for this OS (exe / dmg / deb)
./gradlew packageAll -PappVersion=1.2.0
```

Each OS has to build its own native files, so `jpackage` runs on the matching OS. The
workflow in `.github/workflows/build-release.yml` does this on Windows, macOS and Linux.

## Releasing

```
git tag v1.2.0
git push origin v1.2.0
```

The workflow runs the tests, builds all platforms, and creates a GitHub release with every file.
Tags must look like `v1.2.3`. Other pushes build artifacts only, with the version `1.0.<run number>`.

## Using it from a build

The RiftedReality pack build (`tools/pack/build-pack.sh`) calls the jar directly with
`--preset light --no-mapping`. It builds the jar on first use from `../PackObfscurer`, or from
`PACK_OBFUSCATOR_DIR` if you set it.

## Layout

```
src/main/java/me/amjadsmarzug/packobfscurer/
  Main.java                 GUI or CLI entry point (obfuscate, batch, verify, restore)
  core/                     the pipeline (no Swing here, so it is unit tested)
    PackObfuscator.java     read, strip, rename, rewrite, junk, verify, write
    ReferenceRewriter.java  finds and rewrites asset references in JSON
    NamespaceRewriter.java  renames namespaces in JSON strings, keys and paths
    ModelCleaner.java      cube names and groups
    Metadata.java           credits, PNG re-encoding
    VorbisEncoder.java      ffmpeg: junk sounds and tag removal
    JunkGenerator.java      unique decoys
    PackVerifier.java       problem checks
    PackRestorer.java       undo a mapping
    PackBatch.java          a folder of packs
    PackSource.java         folder and zip input and output
    PackOptions.java        options, presets and the builder
    Json.java               small JSON reader and writer, no dependencies
  ui/                       Swing window, dark theme and custom widgets
src/test/java/              unit tests
```
