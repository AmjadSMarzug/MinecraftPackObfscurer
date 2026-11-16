package me.amjadsmarzug.packobfscurer.update;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UpdateTest {

    @Test
    void versionsCompareNumberByNumber() {
        assertTrue(Version.parse("2.10.0").orElseThrow().compareTo(Version.parse("2.9.9").orElseThrow()) > 0);
        assertEquals(0, Version.parse("v2.1").orElseThrow().compareTo(Version.parse("2.1.0").orElseThrow()));
        assertEquals(0, Version.parse("2.1.0-beta").orElseThrow().compareTo(Version.parse("2.1.0").orElseThrow()),
                "a suffix after the number is ignored");
        assertEquals(Optional.empty(), Version.parse("not a version"));
    }

    @Test
    void checksumListIsReadByFileName() {
        String sums = "aaa111  PackObfuscator-2.1.0.exe\nBBB222  packobfuscator-2.1.0-1.x86_64.rpm\n";
        assertEquals("bbb222", Installers.expectedHash(sums, "packobfuscator-2.1.0-1.x86_64.rpm"));
        assertNull(Installers.expectedHash(sums, "missing.dmg"));
    }

    @Test
    void installerIsPickedPerKind() {
        Release r = new Release("v2.1.0", Version.parse("2.1.0").orElseThrow(), "page", List.of(
                new Release.Asset("PackObfuscator-2.1.0.exe", "u1", 1),
                new Release.Asset("PackObfuscator-2.1.0.dmg", "u2", 1),
                new Release.Asset("packobfuscator-2.1.0-1.x86_64.rpm", "u3", 1),
                new Release.Asset("PackObfuscator-2.1.0-linux-portable.zip", "u4", 1)));
        assertEquals("u1", Installers.pick(r, Installers.Kind.WINDOWS).apiUrl());
        assertEquals("u2", Installers.pick(r, Installers.Kind.MACOS).apiUrl());
        assertEquals("u3", Installers.pick(r, Installers.Kind.LINUX_RPM).apiUrl());
        assertNull(Installers.pick(r, Installers.Kind.OTHER), "portable installs have no automatic update");
    }

    @Test
    void tamperedFilesAreRefused() throws Exception {
        Path file = Files.createTempFile("pkg", ".rpm");
        Files.write(file, "payload".getBytes(StandardCharsets.UTF_8));
        String right = Installers.sha256(file);
        Installers.verify(file, right);
        assertThrows(java.io.IOException.class, () -> Installers.verify(file, "0".repeat(64)));
        assertThrows(java.io.IOException.class, () -> Installers.verify(file, null), "no published hash means no install");
    }
}
