import java.io.File

plugins {
    java
}

val appVersion: String = (findProperty("appVersion") as String?) ?: "1.0.0"
val appName = "PackObfuscator"
val mainClassName = "me.amjadsmarzug.packobfscurer.Main"

group = "me.amjadsmarzug.packobfscurer"
version = appVersion

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set(appName)
    manifest {
        attributes(
            "Main-Class" to mainClassName,
            "Implementation-Version" to appVersion
        )
    }
}

// ---------------------------------------------------------------------------
// Native packaging. Each OS has to build its own bundles, so CI runs this on
// windows-latest, macos-latest and ubuntu-latest.
// ---------------------------------------------------------------------------

val jpackageInput = layout.buildDirectory.dir("jpackage/input")
val jpackageOutput = layout.buildDirectory.dir("jpackage/output")
val distDir = layout.buildDirectory.dir("dist")

val osName: String = System.getProperty("os.name").lowercase()
val isWindows = osName.contains("win")
val isMac = osName.contains("mac")

// jpackage ships with the JDK that runs Gradle (setup-java provides it in CI).
val jpackageExe: String = File(System.getProperty("java.home"), "bin/" + (if (isWindows) "jpackage.exe" else "jpackage")).absolutePath

val stageJar = tasks.register<Copy>("stageJpackageInput") {
    dependsOn(tasks.jar)
    from(tasks.jar.flatMap { it.archiveFile })
    into(jpackageInput)
}

// The app-image is a self-contained folder with its own Java runtime.
val appImage = tasks.register<Exec>("jpackageAppImage") {
    dependsOn(stageJar)
    group = "distribution"
    description = "Builds a self-contained app folder (portable build)."
    val outDir = jpackageOutput.get().dir("app-image").asFile
    doFirst { outDir.deleteRecursively(); outDir.mkdirs() }
    commandLine(
        jpackageExe,
        "--type", "app-image",
        "--name", appName,
        "--app-version", appVersion,
        "--input", jpackageInput.get().asFile.absolutePath,
        "--main-jar", "$appName-$appVersion.jar",
        "--main-class", mainClassName,
        "--dest", outDir.absolutePath,
        "--vendor", "PackObfuscator",
        "--java-options", "-Xmx1g",
        "--java-options", "-Dfile.encoding=UTF-8"
    )
}

// Portable zip: unzip anywhere and run the launcher inside the folder.
val portableZip = tasks.register<Zip>("portableZip") {
    dependsOn(appImage)
    group = "distribution"
    description = "Zips the app-image into a portable archive."
    val platform = when {
        isWindows -> "windows"
        isMac -> "macos"
        else -> "linux"
    }
    archiveFileName.set("$appName-$appVersion-$platform-portable.zip")
    destinationDirectory.set(distDir)
    // app-image already contains the PackObfuscator/ folder, so copy it to the zip root.
    from(jpackageOutput.get().dir("app-image"))
    from(layout.projectDirectory.file("LICENSE"))
    // Keep the executable bit on Linux and macOS launchers.
    filePermissions { unix("rwxr-xr-x") }
    dirPermissions { unix("rwxr-xr-x") }
}

// Native installers. Each OS builds its own: .exe on Windows, .dmg on macOS, .deb and .rpm on Linux.
fun jpackageArgs(outDir: File, type: List<String>): List<String> =
    listOf(
        jpackageExe,
        "--name", appName,
        "--app-version", appVersion,
        "--input", jpackageInput.get().asFile.absolutePath,
        "--main-jar", "$appName-$appVersion.jar",
        "--main-class", mainClassName,
        "--dest", outDir.absolutePath,
        "--vendor", "PackObfuscator",
        "--description", "Obfuscates Minecraft resource packs",
        "--license-file", layout.projectDirectory.file("LICENSE").asFile.absolutePath,
        "--java-options", "-Xmx1g",
        "--java-options", "-Dfile.encoding=UTF-8"
    ) + type

val installer = tasks.register<Exec>("jpackageInstaller") {
    dependsOn(stageJar)
    group = "distribution"
    description = "Builds the native installer for the current OS (exe, dmg or deb)."
    val outDir = distDir.get().asFile
    doFirst { outDir.mkdirs() }
    val type = when {
        isWindows -> listOf("--type", "exe", "--win-menu", "--win-shortcut", "--win-dir-chooser", "--win-per-user-install")
        isMac -> listOf("--type", "dmg")
        else -> listOf("--type", "deb", "--linux-shortcut", "--linux-menu-group", "Utility")
    }
    commandLine(jpackageArgs(outDir, type))
}

// RPM for Fedora, RHEL and openSUSE. jpackage needs rpmbuild on the PATH (the rpm package on Debian/Ubuntu).
val rpmInstaller = tasks.register<Exec>("jpackageRpm") {
    dependsOn(stageJar)
    group = "distribution"
    description = "Builds the .rpm installer (Linux only)."
    val outDir = distDir.get().asFile
    doFirst { outDir.mkdirs() }
    commandLine(jpackageArgs(outDir, listOf("--type", "rpm", "--linux-shortcut", "--linux-menu-group", "Utility")))
    onlyIf { !isWindows && !isMac }
}

tasks.register("packageAll") {
    group = "distribution"
    description = "Portable archive plus native installers for this OS (includes .rpm on Linux)."
    dependsOn(portableZip, installer)
    dependsOn(rpmInstaller)
}

// CLI-only build: the jar plus launchers for Linux, macOS and Windows. Needs Java 21 on the PATH.
// Built once (on Linux in CI), because the jar and scripts are the same on every system.
val cliZip = tasks.register<Zip>("cliZip") {
    dependsOn(tasks.jar)
    group = "distribution"
    description = "Command-line-only archive: the jar and launchers. Needs Java 21."
    archiveFileName.set("$appName-$appVersion-cli.zip")
    destinationDirectory.set(distDir)
    into("PackObfuscator-cli") {
        from(tasks.jar.flatMap { it.archiveFile }) {
            rename { "PackObfuscator.jar" }
        }
        from(layout.projectDirectory.dir("packaging/cli"))
        from(layout.projectDirectory.file("LICENSE"))
    }
    filePermissions { unix("rwxr-xr-x") }
    dirPermissions { unix("rwxr-xr-x") }
}
