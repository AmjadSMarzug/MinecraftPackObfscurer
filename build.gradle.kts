import java.io.File

plugins {
    java
}

val appVersion: String = (findProperty("appVersion") as String?) ?: "1.0.0"
val appName = "PackObfuscator"
val mainClassName = "com.packobfscurer.Main"

group = "com.packobfscurer"
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
    // Keep the executable bit on Linux and macOS launchers.
    filePermissions { unix("rwxr-xr-x") }
    dirPermissions { unix("rwxr-xr-x") }
}

// Installer per OS: .exe on Windows, .dmg on macOS, .deb on Linux.
val installer = tasks.register<Exec>("jpackageInstaller") {
    dependsOn(stageJar)
    group = "distribution"
    description = "Builds the native installer for the current OS."
    val outDir = distDir.get().asFile
    doFirst { outDir.mkdirs() }
    val args = mutableListOf(
        jpackageExe,
        "--name", appName,
        "--app-version", appVersion,
        "--input", jpackageInput.get().asFile.absolutePath,
        "--main-jar", "$appName-$appVersion.jar",
        "--main-class", mainClassName,
        "--dest", outDir.absolutePath,
        "--vendor", "PackObfuscator",
        "--description", "Obfuscates Minecraft resource packs",
        "--java-options", "-Xmx1g",
        "--java-options", "-Dfile.encoding=UTF-8"
    )
    when {
        isWindows -> args += listOf("--type", "exe", "--win-menu", "--win-shortcut", "--win-dir-chooser", "--win-per-user-install")
        isMac -> args += listOf("--type", "dmg")
        else -> args += listOf("--type", "deb", "--linux-shortcut", "--linux-menu-group", "Utility")
    }
    commandLine(args)
}

tasks.register("packageAll") {
    group = "distribution"
    description = "Portable archive plus native installer for this OS."
    dependsOn(portableZip, installer)
}
