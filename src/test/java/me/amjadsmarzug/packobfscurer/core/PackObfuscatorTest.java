package me.amjadsmarzug.packobfscurer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PackObfuscatorTest {

    /** A small pack with every kind of reference the rewriter has to handle. */
    private static SortedMap<String, byte[]> samplePack() {
        SortedMap<String, byte[]> f = new TreeMap<>();
        put(f, "pack.mcmeta", "{\n  \"pack\": {\n    \"pack_format\": 46,\n    \"description\": \"Sample\"\n  }\n}");
        put(f, "pack.png", "PNG");
        put(f, "assets/minecraft/items/sword.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/gauntlet\"}}");
        put(f, "assets/minecraft/models/item/gauntlet.json",
                "{\n \"parent\": \"minecraft:item/handheld\",\n \"textures\": {\"layer0\": \"minecraft:item/gauntlet\", \"particle\": \"#layer0\"}\n}");
        putBytes(f, "assets/minecraft/textures/item/gauntlet.png", png());
        put(f, "assets/minecraft/textures/item/gauntlet.png.mcmeta", "{\"animation\": {\"frametime\": 2}}");
        put(f, "assets/minecraft/models/block/keep.json", "{\"parent\":\"minecraft:block/cube\"}");
        put(f, "assets/minecraft/models/block/crate.json", "{\"parent\":\"minecraft:block/cube\"}");
        put(f, "assets/ritualvfx/models/item/orb.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"ritualvfx:item/orb\"}}");
        putBytes(f, "assets/ritualvfx/textures/item/orb.png", png());
        put(f, "assets/ritualvfx/sounds/fx/boom.ogg", "OggS\u0000\u0000\u0000\u0000");
        put(f, "assets/ritualvfx/sounds.json", "{\"ritual.boom\":{\"sounds\":[\"ritualvfx:fx/boom\",{\"name\":\"ritualvfx:fx/boom\",\"volume\":0.5}]}}");
        put(f, "assets/ritualvfx/font/icons.json", "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"ritualvfx:item/orb.png\",\"chars\":[\"a\"]}]}");
        put(f, "assets/ritualvfx/atlases/blocks.json", "{\"sources\":[{\"type\":\"single\",\"resource\":\"ritualvfx:item/orb\"}]}");
        put(f, "assets/ritualvfx/models/item/blade.json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"ritualvfx:item/orb\"},"
                + "\"elements\":[{\"name\":\"minecraft:item/blade_tip\",\"from\":[0,0,0],\"to\":[16,16,16]},"
                + "{\"name\":\"handle\",\"from\":[1,1,1],\"to\":[2,2,2]}],"
                + "\"groups\":[{\"name\":\"root\",\"origin\":[8,8,8],\"children\":[0,{\"name\":\"nested\",\"children\":[1]}]}],"
                + "\"display\":{}}");
        put(f, ".DS_Store", "junk");
        put(f, "notes/design.psd", "junk");
        put(f, ".git/HEAD", "junk");
        return f;
    }

    private static void putBytes(SortedMap<String, byte[]> f, String path, byte[] bytes) {
        f.put(path, bytes);
    }

    private static byte[] png() {
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            img.setRGB(3, 3, 0xFF336699);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static boolean verifyClean(ObfuscationResult r) {
        return r.warnings().stream().noneMatch(w -> w.startsWith("verify:"));
    }

    private static void put(SortedMap<String, byte[]> f, String path, String text) {
        f.put(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFolder(Path dir, SortedMap<String, byte[]> files) throws IOException {
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            Path p = dir.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
    }

    private static SortedMap<String, byte[]> readZip(Path zip) throws IOException {
        return PackSource.read(zip);
    }

    private static String text(SortedMap<String, byte[]> f, String path) {
        return new String(f.get(path), StandardCharsets.UTF_8);
    }

    @Test
    void renamesOwnedAssetsAndRewritesEveryReference(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("pack");
        writeFolder(src, samplePack());
        Path out = tmp.resolve("out.zip");

        ObfuscationResult r = PackObfuscator.run(src, out, PackOptions.defaults(), s -> { });
        SortedMap<String, byte[]> result = readZip(out);

        // junk is gone
        assertFalse(result.containsKey(".DS_Store"));
        assertFalse(result.containsKey("notes/design.psd"));
        assertFalse(result.keySet().stream().anyMatch(k -> k.startsWith(".git/")));
        assertEquals(3, r.strippedFiles());

        // the vanilla-ish protected block model keeps its name
        assertTrue(result.containsKey("assets/minecraft/models/block/keep.json"));
        assertTrue(result.containsKey("assets/minecraft/models/block/crate.json"));

        // the item model and texture got new names, and the texture's mcmeta moved with it
        String oldModel = "assets/minecraft/models/item/gauntlet.json";
        assertFalse(result.containsKey(oldModel));
        assertFalse(result.containsKey("assets/minecraft/textures/item/gauntlet.png"));
        assertTrue(result.keySet().stream().anyMatch(k -> k.matches("assets/minecraft/textures/item/[0-9a-f]{8}\\.png")));
        assertTrue(result.keySet().stream().anyMatch(k -> k.matches("assets/minecraft/textures/item/[0-9a-f]{8}\\.png\\.mcmeta")));

        // references follow the rename: find the new model name from the item definition
        String itemDef = text(result, "assets/minecraft/items/sword.json");
        String newModel = itemDef.replaceAll(".*\"model\":\"minecraft:item/([0-9a-f]{8})\".*", "$1");
        assertNotEquals(itemDef, newModel, "item def still points at the old model: " + itemDef);
        String modelFile = "assets/minecraft/models/item/" + newModel + ".json";
        assertTrue(result.containsKey(modelFile), "missing " + modelFile);

        String model = text(result, modelFile);
        assertTrue(model.contains("\"parent\":\"minecraft:item/handheld\""), "vanilla parent must stay: " + model);
        assertTrue(model.contains("\"particle\":\"#layer0\""), "variable refs must stay: " + model);
        String texHex = model.replaceAll(".*\"layer0\":\"minecraft:item/([0-9a-f]{8})\".*", "$1");
        assertTrue(result.containsKey("assets/minecraft/textures/item/" + texHex + ".png"));

        // ritualvfx namespace: model, texture, sound, font and atlas references all rewritten
        String orbModel = result.keySet().stream()
                .filter(k -> k.startsWith("assets/ritualvfx/models/item/")).findFirst().orElseThrow();
        String orbText = text(result, orbModel);
        String orbTex = orbText.replaceAll(".*\"layer0\":\"ritualvfx:item/([0-9a-f]{8})\".*", "$1");
        assertNotEquals(orbText, orbTex);
        assertTrue(result.containsKey("assets/ritualvfx/textures/item/" + orbTex + ".png"));

        String soundsJson = text(result, "assets/ritualvfx/sounds.json");
        assertTrue(soundsJson.contains("\"ritual.boom\""), "event keys are not renamed: " + soundsJson);
        assertFalse(soundsJson.contains("fx/boom\""), "sound refs must be rewritten: " + soundsJson);
        String soundHex = soundsJson.replaceAll(".*\"ritualvfx:fx/([0-9a-f]{8})\".*", "$1");
        assertNotEquals(soundsJson, soundHex);
        assertTrue(result.containsKey("assets/ritualvfx/sounds/fx/" + soundHex + ".ogg"));

        String font = text(result, "assets/ritualvfx/font/icons.json");
        assertTrue(font.contains("\"file\":\"ritualvfx:item/" + orbTex + ".png\""), font);
        String atlas = text(result, "assets/ritualvfx/atlases/blocks.json");
        assertTrue(atlas.contains("\"resource\":\"ritualvfx:item/" + orbTex + "\""), atlas);

        assertEquals(1, r.renamedSounds());
        assertTrue(verifyClean(r), r.warnings().toString());
    }

    @Test
    void sameSeedGivesSameNames(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("pack");
        writeFolder(src, samplePack());
        PackOptions opts = new PackOptions.Builder().seed(42L).junk(PackOptions.Junk.NONE).build();
        PackObfuscator.run(src, tmp.resolve("a.zip"), opts, s -> { });
        PackObfuscator.run(src, tmp.resolve("b.zip"), opts, s -> { });
        assertEquals(readZip(tmp.resolve("a.zip")).keySet(), readZip(tmp.resolve("b.zip")).keySet());
    }

    @Test
    void mappingFileIsWrittenNextToOutput(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("pack");
        writeFolder(src, samplePack());
        Path out = tmp.resolve("MyPack-obfuscated.zip");
        ObfuscationResult r = PackObfuscator.run(src, out, PackOptions.defaults(), s -> { });
        assertNotNull(r.mappingFile());
        assertEquals("MyPack-obfuscated.mapping.json", r.mappingFile().getFileName().toString());
        String map = Files.readString(r.mappingFile());
        assertTrue(map.contains("\"minecraft:item/gauntlet\""), map);
    }

    @Test
    void zipInputWithWrapperFolderWorks(@TempDir Path tmp) throws Exception {
        SortedMap<String, byte[]> wrapped = new TreeMap<>();
        for (Map.Entry<String, byte[]> e : samplePack().entrySet()) {
            wrapped.put("MyPack/" + e.getKey(), e.getValue());
        }
        Path zip = tmp.resolve("MyPack.zip");
        PackSource.writeZip(zip, wrapped);
        ObfuscationResult r = PackObfuscator.run(zip, tmp.resolve("out.zip"), PackOptions.defaults(), s -> { });
        assertTrue(r.renamedTextures() >= 2);
        assertTrue(readZip(tmp.resolve("out.zip")).containsKey("pack.mcmeta"));
    }

    @Test
    void refusesFolderWithoutPackMcmeta(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("readme.txt"), "not a pack");
        IOException ex = assertThrows(IOException.class,
                () -> PackObfuscator.run(tmp, tmp.resolve("out.zip"), PackOptions.defaults(), s -> { }));
        assertTrue(ex.getMessage().contains("pack.mcmeta"));
    }

    @Test
    void brokenJsonStopsTheRunAndNamesTheFile(@TempDir Path tmp) throws Exception {
        SortedMap<String, byte[]> f = samplePack();
        put(f, "assets/minecraft/models/item/broken.json", "{\"parent\": }");
        writeFolder(tmp.resolve("pack"), f);
        Path out = tmp.resolve("out.zip");
        IOException ex = assertThrows(IOException.class,
                () -> PackObfuscator.run(tmp.resolve("pack"), out, PackOptions.defaults(), s -> { }));
        assertTrue(ex.getMessage().contains("broken.json"), ex.getMessage());
        assertFalse(Files.exists(out), "nothing should be written when a file is broken");
    }

    @Test
    void descriptionOverrideAndNoRenameKeepsNames(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("pack");
        writeFolder(src, samplePack());
        PackOptions opts = new PackOptions.Builder().renameModels(false).renameTextures(false).renameSounds(false)
                .renameCubes(false).removeGroups(false).descriptionOverride("Hello")
                .junk(PackOptions.Junk.NONE).build();
        Path out = tmp.resolve("out.zip");
        PackObfuscator.run(src, out, opts, s -> { });
        SortedMap<String, byte[]> result = readZip(out);
        assertTrue(result.containsKey("assets/minecraft/textures/item/gauntlet.png"));
        assertTrue(text(result, "pack.mcmeta").contains("\"description\":\"Hello\""));
    }

    @Test
    void refusesToOverwriteInput(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve("pack.zip");
        PackSource.writeZip(zip, samplePack());
        assertThrows(IOException.class,
                () -> PackObfuscator.run(zip, zip, PackOptions.defaults(), s -> { }));
    }

    @Test
    void defaultOutputNaming() {
        assertEquals("MyPack-obfuscated.zip",
                PackObfuscator.defaultOutput(Path.of("/packs/MyPack")).getFileName().toString());
        assertEquals("MyPack-obfuscated.zip",
                PackObfuscator.defaultOutput(Path.of("/packs/MyPack.zip")).getFileName().toString());
    }

    private static String findModel(SortedMap<String, byte[]> result, String marker) {
        return result.keySet().stream()
                .filter(k -> k.startsWith("assets/ritualvfx/models/item/") && text(result, k).contains(marker))
                .findFirst().orElseThrow(() -> new AssertionError("no model contains " + marker));
    }

    @Test
    void cubeNamesAreReplacedAndGroupsRemoved(@TempDir Path tmp) throws Exception {
        writeFolder(tmp.resolve("pack"), samplePack());
        Path out = tmp.resolve("out.zip");
        ObfuscationResult r = PackObfuscator.run(tmp.resolve("pack"), out, PackOptions.defaults(), s -> { });
        SortedMap<String, byte[]> result = readZip(out);

        String blade = text(result, findModel(result, "\"from\":[1,1,1]"));
        assertFalse(blade.contains("blade_tip"), blade);
        assertFalse(blade.contains("handle"), blade);
        assertFalse(blade.contains("\"groups\""), blade);
        assertFalse(blade.contains("nested"), blade);
        assertTrue(blade.matches("(?s).*\"name\":\"[0-9a-f]{8}\".*"), blade);
        assertTrue(blade.contains("\"from\":[0,0,0]"), "geometry must survive: " + blade);
        assertTrue(blade.contains("\"parent\":\"minecraft:item/generated\""), blade);
        assertTrue(blade.matches("(?s).*\"layer0\":\"ritualvfx:item/[0-9a-f]{8}\".*"), blade);
        assertEquals(2, r.renamedCubes());
        assertEquals(2, r.removedGroups());
    }

    @Test
    void keepGroupsRenamesOnlyTheirLabels(@TempDir Path tmp) throws Exception {
        writeFolder(tmp.resolve("pack"), samplePack());
        PackOptions opts = PackOptions.defaults().toBuilder().removeGroups(false).junk(PackOptions.Junk.NONE).build();
        Path out = tmp.resolve("out.zip");
        ObfuscationResult r = PackObfuscator.run(tmp.resolve("pack"), out, opts, s -> { });
        SortedMap<String, byte[]> result = readZip(out);

        String blade = text(result, findModel(result, "\"from\":[1,1,1]"));
        assertTrue(blade.contains("\"groups\""), blade);
        assertTrue(blade.contains("\"children\":[0,"), "group tree must stay intact: " + blade);
        assertFalse(blade.contains("root") || blade.contains("nested"), blade);
        assertEquals(0, r.removedGroups());
        assertEquals(2, r.renamedCubes());
    }

    @Test
    void cubeRenamingCanBeTurnedOff(@TempDir Path tmp) throws Exception {
        writeFolder(tmp.resolve("pack"), samplePack());
        PackOptions opts = new PackOptions.Builder().renameCubes(false).removeGroups(false)
                .junk(PackOptions.Junk.NONE).build();
        Path out = tmp.resolve("out.zip");
        ObfuscationResult r = PackObfuscator.run(tmp.resolve("pack"), out, opts, s -> { });
        String blade = text(readZip(out), findModel(readZip(out), "\"from\":[1,1,1]"));
        assertTrue(blade.contains("blade_tip") && blade.contains("handle"), blade);
        assertTrue(blade.contains("\"groups\""), blade);
        assertEquals(0, r.renamedCubes());
    }

    @Test
    void junkAddsDecoysWithoutTouchingRealFiles(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("pack");
        writeFolder(src, samplePack());
        PackOptions plainOpts = new PackOptions.Builder().seed(42L).stripMetadata(false)
                .junk(PackOptions.Junk.NONE).build();
        PackOptions junkOpts = new PackOptions.Builder().seed(42L).stripMetadata(false)
                .junk(new PackOptions.Junk(4, 4, 0)).build();
        Path plain = tmp.resolve("plain.zip");
        Path junky = tmp.resolve("junky.zip");
        PackObfuscator.run(src, plain, plainOpts, s -> { });
        ObfuscationResult r = PackObfuscator.run(src, junky, junkOpts, s -> { });

        SortedMap<String, byte[]> a = readZip(plain);
        SortedMap<String, byte[]> b = readZip(junky);
        for (Map.Entry<String, byte[]> e : a.entrySet()) {
            assertArrayEquals(e.getValue(), b.get(e.getKey()), "real file changed: " + e.getKey());
        }
        Set<String> added = new TreeSet<>(b.keySet());
        added.removeAll(a.keySet());
        List<String> pngs = added.stream().filter(p -> p.endsWith(".png")).toList();
        List<String> models = added.stream().filter(p -> p.contains("/models/")).toList();
        assertEquals(4, pngs.size());
        assertEquals(4, models.size());
        assertEquals(8, r.junkFiles());
        assertTrue(r.junkFolders() > 0);
        assertTrue(verifyClean(r), r.warnings().toString());

        for (String png : pngs) {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(b.get(png)));
            assertNotNull(img, "junk png is not decodable: " + png);
            assertTrue(img.getWidth() == 16 || img.getWidth() == 32, "unexpected size " + img.getWidth());
        }
        for (String model : models) {
            Map<?, ?> doc = (Map<?, ?>) Json.parse(text(b, model));
            assertTrue(doc.containsKey("elements"), model);
            if (doc.get("textures") instanceof Map<?, ?> tex) {
                // a junk model may only point at junk textures
                String ref = (String) tex.get("layer0");
                String ns = ref.substring(0, ref.indexOf(':'));
                String rel = ref.substring(ref.indexOf(':') + 1);
                assertTrue(added.contains("assets/" + ns + "/textures/" + rel + ".png"), ref);
            }
        }
        try (ZipFile zf = new ZipFile(junky.toFile())) {
            assertTrue(zf.stream().anyMatch(z -> z.isDirectory() && z.getName().startsWith("assets/")),
                    "junk folders should be real zip directory entries");
        }
    }

    @Test
    void everyJunkTextureAndModelIsUnique() {
        Random rnd = new Random(1);
        JunkGenerator.Result r = JunkGenerator.generate(new PackOptions.Junk(60, 60, 0),
                Set.of(new AssetKey(AssetKey.Type.TEXTURE, "ritualvfx", "item/orb")),
                Set.of(), rnd, true, Optional.empty(), java.util.Map.of());
        Set<String> textures = new HashSet<>();
        Set<String> models = new HashSet<>();
        for (Map.Entry<String, byte[]> e : r.files().entrySet()) {
            if (e.getKey().endsWith(".png")) {
                assertTrue(textures.add(Arrays.toString(e.getValue())), "duplicate texture " + e.getKey());
            } else if (e.getKey().contains("/models/") && !e.getKey().endsWith("/")) {
                assertTrue(models.add(Arrays.toString(e.getValue())), "duplicate model " + e.getKey());
            }
        }
        assertEquals(60, textures.size());
        assertEquals(60, models.size());
    }

    @Test
    void junkSoundsAreSkippedWithAWarningWithoutFfmpeg() {
        JunkGenerator.Result r = JunkGenerator.generate(new PackOptions.Junk(0, 0, 3),
                Set.of(), Set.of(), new Random(2), true, Optional.empty(), java.util.Map.of());
        assertEquals(0, r.sounds());
        assertTrue(r.files().keySet().stream().noneMatch(k -> k.endsWith(".ogg")));
        assertTrue(r.warnings().get(0).contains("ffmpeg"), r.warnings().toString());
    }

    @Test
    void junkSoundsDifferInLengthAndPitch() {
        Optional<Path> ffmpeg = VorbisEncoder.locate();
        assumeTrue(ffmpeg.isPresent(), "ffmpeg is not installed, so junk sound encoding is not tested");
        JunkGenerator.Result r = JunkGenerator.generate(new PackOptions.Junk(0, 0, 5),
                Set.of(new AssetKey(AssetKey.Type.SOUND, "ritualvfx", "fx/boom")),
                Set.of(), new Random(3), true, ffmpeg, java.util.Map.of());
        assertEquals(5, r.sounds());
        List<byte[]> oggs = r.files().entrySet().stream()
                .filter(e -> e.getKey().endsWith(".ogg")).map(Map.Entry::getValue).toList();
        assertEquals(5, oggs.size());
        Set<String> unique = new HashSet<>();
        Set<Integer> lengths = new HashSet<>();
        for (byte[] ogg : oggs) {
            assertEquals("OggS", new String(ogg, 0, 4, StandardCharsets.US_ASCII), "not an Ogg file");
            assertTrue(unique.add(Arrays.toString(ogg)), "duplicate junk sound");
            lengths.add(ogg.length);
        }
        assertTrue(lengths.size() > 1, "junk sounds should differ in length");
    }

    @Test
    void presetsSetTheExpectedOptions() {
        PackOptions light = PackOptions.forPreset(PackOptions.Preset.LIGHT);
        assertFalse(light.renameCubes());
        assertFalse(light.renameNamespaces());
        assertEquals(0, light.junk().total());
        PackOptions standard = PackOptions.forPreset(PackOptions.Preset.STANDARD);
        assertTrue(standard.renameCubes());
        assertFalse(standard.renameNamespaces());
        PackOptions max = PackOptions.forPreset(PackOptions.Preset.MAX);
        assertTrue(max.renameNamespaces());
        assertTrue(max.junk().total() > standard.junk().total());
        assertEquals(PackOptions.defaults(), standard);
    }

    @Test
    void namespacesAreRenamedButVanillaIsNot(@TempDir Path tmp) throws Exception {
        writeFolder(tmp.resolve("pack"), samplePack());
        PackOptions opts = new PackOptions.Builder().renameNamespaces(true).junk(PackOptions.Junk.NONE)
                .stripMetadata(false).build();
        Path out = tmp.resolve("out.zip");
        ObfuscationResult r = PackObfuscator.run(tmp.resolve("pack"), out, opts, s -> { });
        SortedMap<String, byte[]> result = readZip(out);

        assertTrue(result.keySet().stream().noneMatch(k -> k.startsWith("assets/ritualvfx/")), "old namespace still present");
        assertTrue(result.keySet().stream().anyMatch(k -> k.startsWith("assets/minecraft/")), "vanilla namespace must stay");
        String mapping = Files.readString(r.mappingFile());
        String newNs = Json.parse(mapping) instanceof Map<?, ?> m
                ? (String) ((Map<?, ?>) m.get("namespaces")).get("ritualvfx") : null;
        assertNotNull(newNs);
        assertTrue(newNs.matches("[a-z][a-z0-9]{3,6}"), newNs);
        assertTrue(result.keySet().stream().anyMatch(k -> k.startsWith("assets/" + newNs + "/")));
        for (Map.Entry<String, byte[]> e : result.entrySet()) {
            if (e.getKey().endsWith(".json")) {
                assertFalse(text(result, e.getKey()).contains("ritualvfx:"), "old namespace in " + e.getKey());
            }
        }
        assertTrue(verifyClean(r), r.warnings().toString());
    }

    @Test
    void restoreUndoesRenamesNamespacesAndJunk(@TempDir Path tmp) throws Exception {
        writeFolder(tmp.resolve("pack"), samplePack());
        PackOptions opts = new PackOptions.Builder().renameNamespaces(true).renameCubes(false).removeGroups(false)
                .stripMetadata(false).junk(new PackOptions.Junk(3, 3, 0)).build();
        Path out = tmp.resolve("out.zip");
        ObfuscationResult r = PackObfuscator.run(tmp.resolve("pack"), out, opts, s -> { });
        SortedMap<String, byte[]> obfuscated = readZip(out);

        PackRestorer.Result restored = PackRestorer.restore(obfuscated, Files.readString(r.mappingFile()));
        SortedMap<String, byte[]> original = samplePack();
        Set<String> expectedKeys = new TreeSet<>(original.keySet());
        expectedKeys.removeIf(PackObfuscator::isJunk);
        assertEquals(expectedKeys, new TreeSet<>(restored.files().keySet()));
        assertEquals(6, restored.removedJunk());
        for (String path : expectedKeys) {
            byte[] want = original.get(path);
            byte[] got = restored.files().get(path);
            if (path.endsWith(".json") || path.endsWith(".mcmeta")) {
                assertEquals(Json.parse(new String(want, StandardCharsets.UTF_8)),
                        Json.parse(new String(got, StandardCharsets.UTF_8)), "restored JSON differs: " + path);
            } else {
                assertArrayEquals(want, got, "restored file differs: " + path);
            }
        }
    }

    @Test
    void restoreRejectsOldMappings() {
        SortedMap<String, byte[]> files = samplePack();
        IOException ex = assertThrows(IOException.class, () -> PackRestorer.restore(files,
                "{\"format\":\"packobfuscator-mapping\",\"version\":1}"));
        assertTrue(ex.getMessage().contains("older version"), ex.getMessage());
    }

    @Test
    void verifyFindsBrokenReferencesJsonAndImages() {
        SortedMap<String, byte[]> files = new TreeMap<>();
        put(files, "pack.mcmeta", "{\"pack\":{\"pack_format\":46,\"description\":\"x\"}}");
        put(files, "assets/abc/models/item/m.json", "{\"parent\":\"abc:item/missing\"}");
        put(files, "assets/abc/models/item/bad.json", "{\"parent\": }");
        putBytes(files, "assets/abc/textures/item/t.png", "not a png".getBytes(StandardCharsets.UTF_8));
        List<String> problems = PackVerifier.verify(files);
        assertEquals(3, problems.size(), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("abc:item/missing")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("invalid JSON")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("PNG")));
    }

    @Test
    void metadataIsStrippedAndZipTimesAreFixed(@TempDir Path tmp) throws Exception {
        SortedMap<String, byte[]> f = samplePack();
        put(f, "assets/ritualvfx/models/item/credited.json",
                "{\"credit\":\"made by someone\",\"parent\":\"minecraft:item/generated\",\"elements\":[]}");
        writeFolder(tmp.resolve("pack"), f);
        Path out = tmp.resolve("out.zip");
        PackObfuscator.run(tmp.resolve("pack"), out, PackOptions.defaults(), s -> { });
        SortedMap<String, byte[]> result = readZip(out);
        for (String k : result.keySet()) {
            if (k.endsWith(".json") && k.contains("/models/") && text(result, k).contains("\"elements\":[]")) {
                assertFalse(text(result, k).contains("credit"), "credit left in " + k);
            }
        }
        try (ZipFile zf = new ZipFile(out.toFile())) {
            Set<Long> times = new TreeSet<>();
            zf.stream().forEach(z -> times.add(z.getTime()));
            assertEquals(1, times.size(), "every entry should share one timestamp");
        }
    }

    @Test
    void batchObfuscatesEveryPackAndKeepsGoingAfterAFailure(@TempDir Path tmp) throws Exception {
        Path dir = tmp.resolve("packs");
        writeFolder(dir.resolve("GoodOne"), samplePack());
        SortedMap<String, byte[]> zipped = samplePack();
        PackSource.writeZip(dir.resolve("GoodTwo.zip"), zipped);
        SortedMap<String, byte[]> broken = new TreeMap<>();
        put(broken, "pack.mcmeta", "{\"pack\":{\"pack_format\":46,\"description\":\"x\"}}");
        put(broken, "assets/abc/models/item/bad.json", "{\"parent\": }");
        writeFolder(dir.resolve("Broken"), broken);

        List<PackBatch.Item> items = PackBatch.run(dir, dir.resolve("obfuscated"),
                PackOptions.defaults(), s -> { });
        assertEquals(3, items.size());
        assertEquals(1, items.stream().filter(i -> i.error() != null).count());
        assertTrue(Files.exists(dir.resolve("obfuscated/GoodOne-obfuscated.zip")));
        assertTrue(Files.exists(dir.resolve("obfuscated/GoodTwo-obfuscated.zip")));
    }

    @Test
    void someJunkTexturesCarryRealPixelsAndModelsUseSeveralTextures() throws Exception {
        java.awt.image.BufferedImage red = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                red.setRGB(x, y, 0xFFFF0000);
            }
        }
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        ImageIO.write(red, "png", bos);
        SortedMap<String, byte[]> source = new TreeMap<>();
        source.put("assets/abc/textures/item/red.png", bos.toByteArray());

        JunkGenerator.Result r = JunkGenerator.generate(new PackOptions.Junk(12, 24, 0),
                Set.of(), Set.of(), new Random(9), true, Optional.empty(), source);

        int withRealPixels = 0;
        for (Map.Entry<String, byte[]> e : r.files().entrySet()) {
            if (!e.getKey().endsWith(".png")) {
                continue;
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(e.getValue()));
            int pureRed = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    if (img.getRGB(x, y) == 0xFFFF0000) {
                        pureRed++;
                    }
                }
            }
            if (pureRed >= 16) {
                withRealPixels++;
            }
        }
        assertTrue(withRealPixels > 0, "some junk textures should carry the copied real pixels");

        int multiSlot = 0;
        for (Map.Entry<String, byte[]> e : r.files().entrySet()) {
            if (e.getKey().contains("/models/") && !e.getKey().endsWith("/")
                    && new String(e.getValue(), StandardCharsets.UTF_8).contains("\"layer1\"")) {
                multiSlot++;
            }
        }
        assertTrue(multiSlot > 0, "some junk models should use more than one texture slot");
    }
}
