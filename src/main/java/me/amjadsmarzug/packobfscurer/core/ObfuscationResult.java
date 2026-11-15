package me.amjadsmarzug.packobfscurer.core;

import java.nio.file.Path;
import java.util.List;

/** What an obfuscation run did. Shown in the UI and printed by the CLI. */
public record ObfuscationResult(
        Path output,
        Path mappingFile,
        int inputFiles,
        int outputFiles,
        long inputBytes,
        long outputBytes,
        int renamedModels,
        int renamedTextures,
        int renamedSounds,
        int renamedCubes,
        int removedGroups,
        int junkFiles,
        int junkFolders,
        int strippedFiles,
        int minifiedJson,
        List<String> warnings,
        long durationMillis
) {
    public ObfuscationResult {
        warnings = List.copyOf(warnings);
    }
}
