package me.amjadsmarzug.packobfscurer.core;

import java.util.List;

/**
 * Everything the obfuscator can be told to do.
 *
 * @param renameModels      give the pack's model files random names
 * @param renameTextures    give the pack's texture files random names
 * @param renameSounds      give the pack's sound files random names
 * @param renameNamespaces  give each pack namespace (assets/&lt;name&gt;) a random name. Never touches "minecraft".
 * @param renameCubes       give every cube (element) in a model a random name, and group names if groups are kept
 * @param removeGroups      delete the "groups" tree from models (editor-only data)
 * @param minifyJson        write every JSON file without whitespace
 * @param stripJunk         drop editor, git and OS leftovers (.psd, .DS_Store, .git and so on)
 * @param stripMetadata     remove author credits, PNG text chunks and Vorbis tags, fix zip dates,
 *                          and shuffle zip entry order and JSON key order
 * @param writeMapping      save an old-to-new name map next to the output (needed to undo or debug)
 * @param seed              fixed seed for reproducible names, or null for random names
 * @param protectedPrefixes asset paths (relative to assets/, e.g. "minecraft/models/block/")
 *                          that keep their names because vanilla code may load them directly
 * @param descriptionOverride text for pack.mcmeta's description, or null to keep it
 * @param junk              decoy models, textures and sounds to add (see {@link Junk})
 */
public record PackOptions(
        boolean renameModels,
        boolean renameTextures,
        boolean renameSounds,
        boolean renameNamespaces,
        boolean renameCubes,
        boolean removeGroups,
        boolean minifyJson,
        boolean stripJunk,
        boolean stripMetadata,
        boolean writeMapping,
        Long seed,
        List<String> protectedPrefixes,
        String descriptionOverride,
        Junk junk
) {

    /** Named starting points. Every option can still be changed afterwards. */
    public enum Preset {
        /** Renames models, textures and sounds, minifies, strips junk. No model cleanup, no decoys. */
        LIGHT,
        /** Light, plus cube and group cleanup and a moderate amount of junk. The default. */
        STANDARD,
        /** Everything: namespaces are renamed and there is a lot of junk. */
        MAX;

        public String label() {
            return name().charAt(0) + name().substring(1).toLowerCase();
        }

        public static Preset fromName(String name) {
            for (Preset p : values()) {
                if (p.name().equalsIgnoreCase(name)) {
                    return p;
                }
            }
            throw new IllegalArgumentException("Unknown preset '" + name + "'. Use light, standard or max.");
        }
    }

    /** How many decoy files to add. Zero means none. */
    public record Junk(int models, int textures, int sounds) {
        public static final Junk NONE = new Junk(0, 0, 0);
        public static final int MAX = 1000;

        public Junk {
            models = clamp(models);
            textures = clamp(textures);
            sounds = clamp(sounds);
        }

        public int total() {
            return models + textures + sounds;
        }

        private static int clamp(int n) {
            return Math.max(0, Math.min(MAX, n));
        }
    }

    /** Folders whose names vanilla Minecraft loads directly. Renaming them can silently break the game. */
    public static final List<String> DEFAULT_PROTECTED = List.of(
            "minecraft/atlases/",
            "minecraft/blockstates/",
            "minecraft/font/",
            "minecraft/lang/",
            "minecraft/shaders/",
            "minecraft/texts/",
            "minecraft/particles/",
            "minecraft/models/block/",
            "minecraft/textures/block/",
            "minecraft/textures/entity/",
            "minecraft/textures/gui/",
            "minecraft/textures/particle/",
            "minecraft/textures/environment/",
            "minecraft/textures/colormap/",
            "minecraft/textures/misc/",
            "minecraft/textures/painting/"
    );

    public PackOptions {
        protectedPrefixes = protectedPrefixes == null ? List.of() : List.copyOf(protectedPrefixes);
        if (descriptionOverride != null && descriptionOverride.isBlank()) {
            descriptionOverride = null;
        }
        junk = junk == null ? Junk.NONE : junk;
    }

    public static PackOptions defaults() {
        return forPreset(Preset.STANDARD);
    }

    public static PackOptions forPreset(Preset preset) {
        return switch (preset) {
            case LIGHT -> new Builder().build();
            case STANDARD -> new Builder()
                    .renameCubes(true).removeGroups(true)
                    .junk(new Junk(25, 25, 5)).build();
            case MAX -> new Builder()
                    .renameNamespaces(true)
                    .renameCubes(true).removeGroups(true)
                    .junk(new Junk(60, 60, 12)).build();
        };
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Sets options one at a time. Starts from a preset or from an existing set of options. */
    public static final class Builder {
        private boolean renameModels = true;
        private boolean renameTextures = true;
        private boolean renameSounds = true;
        private boolean renameNamespaces = false;
        private boolean renameCubes = false;
        private boolean removeGroups = false;
        private boolean minifyJson = true;
        private boolean stripJunk = true;
        private boolean stripMetadata = true;
        private boolean writeMapping = true;
        private Long seed;
        private List<String> protectedPrefixes = DEFAULT_PROTECTED;
        private String descriptionOverride;
        private Junk junk = Junk.NONE;

        /** Starts from the Light preset. */
        public Builder() {
        }

        private Builder(PackOptions o) {
            renameModels = o.renameModels();
            renameTextures = o.renameTextures();
            renameSounds = o.renameSounds();
            renameNamespaces = o.renameNamespaces();
            renameCubes = o.renameCubes();
            removeGroups = o.removeGroups();
            minifyJson = o.minifyJson();
            stripJunk = o.stripJunk();
            stripMetadata = o.stripMetadata();
            writeMapping = o.writeMapping();
            seed = o.seed();
            protectedPrefixes = o.protectedPrefixes();
            descriptionOverride = o.descriptionOverride();
            junk = o.junk();
        }

        public Builder renameModels(boolean v) { renameModels = v; return this; }
        public Builder renameTextures(boolean v) { renameTextures = v; return this; }
        public Builder renameSounds(boolean v) { renameSounds = v; return this; }
        public Builder renameNamespaces(boolean v) { renameNamespaces = v; return this; }
        public Builder renameCubes(boolean v) { renameCubes = v; return this; }
        public Builder removeGroups(boolean v) { removeGroups = v; return this; }
        public Builder minifyJson(boolean v) { minifyJson = v; return this; }
        public Builder stripJunk(boolean v) { stripJunk = v; return this; }
        public Builder stripMetadata(boolean v) { stripMetadata = v; return this; }
        public Builder writeMapping(boolean v) { writeMapping = v; return this; }
        public Builder seed(Long v) { seed = v; return this; }
        public Builder protectedPrefixes(List<String> v) { protectedPrefixes = v; return this; }
        public Builder descriptionOverride(String v) { descriptionOverride = v; return this; }
        public Builder junk(Junk v) { junk = v; return this; }

        public PackOptions build() {
            return new PackOptions(renameModels, renameTextures, renameSounds, renameNamespaces,
                    renameCubes, removeGroups, minifyJson, stripJunk, stripMetadata, writeMapping,
                    seed, protectedPrefixes, descriptionOverride, junk);
        }
    }
}
