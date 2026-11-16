# Pack Obfuscator

Pack Obfuscator makes a resource pack hard to read. It renames models, textures, sounds and namespaces,
rewrites the references to match, strips metadata, adds decoy files, and checks the result before writing
a new zip. Your original pack is not changed.

## Install

Download a release and run it. There are two kinds of release for each version.

- **Desktop** (`vX.Y.Z`): a portable archive for Windows, macOS and Linux, plus an installer
  (`.exe`, `.dmg`, `.deb` or `.rpm`). The portable archive includes the command line too.
- **CLI** (`vX.Y.Z-cli`): a zip with only the command line. It needs Java 21 or newer.

To use the command line from any folder, put the launcher on your PATH. For the CLI zip, put the
folder that holds `packobfuscator` (or `packobfuscator.cmd` on Windows) on your PATH.

## Presets

| Preset | What it does |
| --- | --- |
| Light | Renames models, textures and sounds. Minifies JSON and strips junk and metadata. |
| Standard (default) | Light, plus cube and group cleanup, per-part texture splitting, and some decoy files. |
| Max | Standard, plus namespace renaming and more decoy files. |

In the window, pick a preset from the dropdown. Changing any option by hand switches it to Custom.

## Command line

```
packobfuscator <pack folder or .zip> [output.zip] [options]
packobfuscator --batch <folder> [--out <folder>] [options]
packobfuscator --verify <pack folder or .zip>
packobfuscator --restore <obfuscated.zip> --mapping <map.json> [--out <output.zip>]
packobfuscator --check-update
packobfuscator --update
```

Use `--preset light|standard|max` to pick a starting point. Options after it change single steps, for
example `--keep-groups`, `--no-sounds`, `--junk-models 200`, `--seed 42`. Run `packobfuscator --help` for
the full list. Exit code 0 means success, 1 means failure, and 2 means the command was used wrong.

## What it changes

- Removes editor, git and OS leftover files.
- Checks that every JSON file parses before doing anything else.
- Renames models, textures and sounds to random names, and updates every reference.
- Renames namespaces (Max), but never `minecraft` or `realms`.
- Gives the parts of each real model their own PNG. Only the pixels that part uses are kept, and the
  rest is filled with noise. Models it cannot check safely are left alone.
- Replaces cube and group names, and removes the group tree, which the game does not read.
- Strips credits, PNG text, and Vorbis tags from sounds, fixes zip timestamps, and shuffles the order of
  entries and JSON keys.
- Adds decoy models, textures, sounds and folders. None of them are referenced by the real files.
- Checks the output for broken JSON, undecodable images, non-Ogg sounds and missing references.
- Writes a name map, `<name>-obfuscated.mapping.json`, that can undo the renames.

Every pack also gets the credit "Obfuscated by AmjadSMarzug with PackObfuscator" in its description, in
an `OBFUSCATED_BY.txt` file, and in the zip comment. The credit can't be turned off.

## Restoring

`--restore` undoes renames, namespaces, references and decoys. It can't bring back cube and group names,
removed credits or metadata, the original formatting, or the textures that were split. Keep the original
pack if you need those.

## Requirements

ffmpeg on the PATH is needed for two things: making the decoy sounds, and removing Vorbis tags from
sounds. Without it, both steps are skipped with a warning. `scripts/install-ffmpeg.sh` (Linux and macOS)
or `scripts/install-ffmpeg.ps1` (Windows) installs it.

## Things to check before publishing

- The game loads some folders by fixed name, so those are kept by default: particles, GUI, block models
  and textures, fonts, language files, atlases, and a few others. The list is editable in the window.
- If a pack replaces a vanilla model, texture or sound, the replacement only works if everything that uses
  it is also replaced. Test in game.
- The checks can't see vanilla assets, so `minecraft:` references are not verified.
- A directory atlas loads every texture under its folder. Decoys live in random folders, so they only
  appear in an atlas if you point one at the pack root.
- Split textures were checked against the original pixels with a script. Check a few split models in
  game too.
- The Windows and macOS builds are not code-signed, so the operating system will warn on first launch.

## Updates

The app checks for a newer release once a day. When one is found, a bar appears at the top with an
Update button. It asks first, downloads the installer, checks it against the published checksums, and
then starts it. Linux installs from RPM use `pkexec`. Windows and macOS open the installer for you to
finish. Portable Linux archives can't update themselves, so the bar opens the release page instead.

If the releases are private, set `PACKOBF_UPDATE_TOKEN` to a token that can read them.

## License

All rights reserved. See [LICENSE](LICENSE).
