package sh.harold.blackbox.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import java.io.IOException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject

abstract class RunHytaleExtension @Inject constructor(objects: ObjectFactory) {
    val jarUrl: Property<String> = objects.property(String::class.java)
    val assetsPath: Property<String> = objects.property(String::class.java)
    val serverArgs: ListProperty<String> = objects.listProperty(String::class.java).convention(listOf("--allow-op"))
}

abstract class HytaleServerProcessService : BuildService<BuildServiceParameters.None>, AutoCloseable {
    private val logger = Logging.getLogger(HytaleServerProcessService::class.java)

    @Volatile
    private var process: Process? = null

    fun register(process: Process) {
        this.process = process
    }

    override fun close() {
        val process = this.process ?: return
        stopProcess(logger, process)
    }
}

class RunHytalePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("java-base")

        val extension = project.extensions.create("runHytale", RunHytaleExtension::class.java)

        val runDir = project.layout.projectDirectory.dir("run")
        val modsDir = runDir.dir("mods")
        val cacheDir = project.layout.buildDirectory.dir("hytale-cache")
        val processService = project.gradle.sharedServices.registerIfAbsent(
            "hytaleServerProcess",
            HytaleServerProcessService::class.java
        ) {}

        project.tasks.register("cleanRun", Delete::class.java) {
            group = "hytale"
            description = "Deletes ./run."
            delete(runDir)
        }

        val runServer = project.tasks.register("runServer") {
            group = "hytale"
            description = "Builds the Hytale mod, prepares ./run, and launches the server."
            outputs.upToDateWhen { false }
            usesService(processService)

            doLast {
                val jarUrl = extension.jarUrl.orNull ?: throw GradleException(
                    "runHytale.jarUrl is not set. Configure it in the root build (or set -Phytale.jarUrl / HYTALE_JAR_URL)."
                )
                val assetsPath = extension.assetsPath.orNull ?: throw GradleException(
                    "runHytale.assetsPath is not set. Set -Phytale.assetsPath=/path/to/Assets.zip (or export HYTALE_ASSETS_PATH)."
                )

                prepareRunDir(project, runDir, modsDir)
                val cachedServerJar = ensureCachedServerJar(project, cacheDir.get().asFile.toPath(), jarUrl)
                copyServerJarToRunDir(project, cachedServerJar, runDir.asFile.toPath())
                copyPluginJarToRunDir(project, modsDir.asFile.toPath())

                startServer(project, runDir.asFile, assetsPath, extension.serverArgs.get(), processService.get())
            }
        }

        project.gradle.projectsEvaluated {
            val hytaleProject = project.project(":blackbox-hytale")
            val chosenTask = hytaleProject.tasks.findByName("shadowJar") ?: hytaleProject.tasks.findByName("jar")
                ?: throw GradleException("Expected :blackbox-hytale to have a 'jar' task (or 'shadowJar').")
            runServer.configure { dependsOn(chosenTask) }
        }
    }
}

private fun prepareRunDir(project: Project, runDir: Directory, modsDir: Directory) {
    Files.createDirectories(runDir.asFile.toPath())
    Files.createDirectories(modsDir.asFile.toPath())
    project.logger.lifecycle("Run dir: ${runDir.asFile}")
}

private fun ensureCachedServerJar(project: Project, cacheDir: Path, jarUrl: String): Path {
    Files.createDirectories(cacheDir)

    val cacheKey = sha256Hex(jarUrl)
    val cachedJar = cacheDir.resolve("$cacheKey.jar")
    if (Files.exists(cachedJar)) {
        project.logger.lifecycle("Using cached Hytale server jar: $cachedJar")
        return cachedJar
    }

    project.logger.lifecycle("Caching Hytale server jar from: $jarUrl")

    val tmp = cacheDir.resolve("$cacheKey.jar.part")
    Files.deleteIfExists(tmp)

    try {
        fetchUrlToPath(project, jarUrl, tmp)
        try {
            Files.move(tmp, cachedJar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp, cachedJar, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(tmp)
    }

    return cachedJar
}

private fun fetchUrlToPath(project: Project, jarUrl: String, destination: Path) {
    val parsedUri = runCatching { URI(jarUrl) }.getOrNull()
    if (parsedUri != null && parsedUri.scheme != null) {
        if (parsedUri.scheme == "file") {
            val source = Path.of(parsedUri)
            requireFileExists(source, "Hytale server jar")
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
            return
        }

        val connection = parsedUri.toURL().openConnection().apply {
            connectTimeout = 30_000
            readTimeout = 30_000
        }
        connection.getInputStream().use { input ->
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING)
        }
        return
    }

    val source = project.file(jarUrl).toPath()
    requireFileExists(source, "Hytale server jar")
    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
}

private fun copyServerJarToRunDir(project: Project, cachedJar: Path, runDir: Path) {
    val target = runDir.resolve("HytaleServer.jar")
    Files.copy(cachedJar, target, StandardCopyOption.REPLACE_EXISTING)
    project.logger.lifecycle("Server jar: $target")
}

private fun copyPluginJarToRunDir(project: Project, modsDir: Path) {
    val hytaleProject = project.project(":blackbox-hytale")
    val archiveTask = (hytaleProject.tasks.findByName("shadowJar") ?: hytaleProject.tasks.findByName("jar"))
        ?: throw GradleException("Expected :blackbox-hytale to have a 'jar' task (or 'shadowJar').")
    if (archiveTask !is AbstractArchiveTask) {
        throw GradleException("Expected :blackbox-hytale:${archiveTask.name} to be an archive task.")
    }

    Files.createDirectories(modsDir)

    val pluginJar = archiveTask.archiveFile.get().asFile.toPath()
    modsDir.toFile().listFiles { file -> file.isFile && file.name.startsWith("blackbox-hytale") && file.name.endsWith(".jar") }
        ?.forEach { it.delete() }
    val target = modsDir.resolve(pluginJar.fileName)
    Files.copy(pluginJar, target, StandardCopyOption.REPLACE_EXISTING)
    project.logger.lifecycle("Mod jar: $target")
}

private fun startServer(
    project: Project,
    runDir: File,
    assetsPathRaw: String,
    serverArgs: List<String>,
    processService: HytaleServerProcessService,
) {
    val javaExecutable = javaExecutable(project)
    val assetsFile = project.file(assetsPathRaw)
    if (!assetsFile.exists()) {
        throw GradleException("Assets not found: ${assetsFile.absolutePath} (set hytale.assetsPath / HYTALE_ASSETS_PATH)")
    }
    val assetsPath = assetsFile.absolutePath

    val command = buildList {
        add(javaExecutable.absolutePath)
        if (project.findProperty("debug") != null) {
            add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
        }
        add("-jar")
        add("HytaleServer.jar")
        add("--assets")
        add(assetsPath)
        add("--disable-sentry")
        addAll(serverArgs)
    }

    project.logger.lifecycle("Starting server: ${command.joinToString(" ")}")

    val hasConsole = System.console() != null
    val processBuilder = ProcessBuilder(command).directory(runDir)

    val process = if (hasConsole) {
        processBuilder.inheritIO().start()
    } else {
        processBuilder.start()
    }
    processService.register(process)

    val stdoutPump = if (hasConsole) null else pumpStream("hytale-server-stdout", process.inputStream, System.out)
    val stderrPump = if (hasConsole) null else pumpStream("hytale-server-stderr", process.errorStream, System.err)
    if (!hasConsole) {
        val stdinPump = pumpInput("hytale-server-stdin", System.`in`, process.outputStream)
        stdinPump.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, exception ->
            project.logger.info("stdin pump crashed", exception)
        }
        project.logger.lifecycle("Console is not a TTY (Gradle daemon). If you can't type commands, re-run with `--no-daemon --console=plain`.")
    }

    try {
        process.waitFor()
    } catch (e: InterruptedException) {
        stopProcess(project.logger, process)
        Thread.currentThread().interrupt()
    } finally {
        stdoutPump?.let { runCatching { it.join(2_000) } }
        stderrPump?.let { runCatching { it.join(2_000) } }
    }
}

private fun stopProcess(logger: Logger, process: Process) {
    if (!process.isAlive) {
        return
    }

    logger.lifecycle("Stopping Hytale server...")
    process.destroy()
    try {
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
        }
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}

private fun requireFileExists(path: Path, label: String) {
    if (!Files.exists(path)) {
        throw GradleException("$label not found: $path")
    }
}

private fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun pumpStream(name: String, input: InputStream, output: OutputStream): Thread {
    val thread = Thread({
        val buffer = ByteArray(8 * 1024)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    return@Thread
                }
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (_: IOException) {
        } finally {
            runCatching { input.close() }
        }
    }, name)
    thread.isDaemon = true
    thread.start()
    return thread
}

private fun pumpInput(name: String, input: InputStream, output: OutputStream): Thread {
    val thread = Thread({
        val buffer = ByteArray(8 * 1024)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    return@Thread
                }
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (_: IOException) {
        }
    }, name)
    thread.isDaemon = true
    thread.start()
    return thread
}

private fun javaExecutable(project: Project): File {
    val toolchains = project.extensions.findByType(JavaToolchainService::class.java)
    if (toolchains != null) {
        val launcher = toolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        }.get()
        return launcher.executablePath.asFile
    }

    val javaHome = File(System.getProperty("java.home"))
    val unix = File(javaHome, "bin/java")
    if (unix.isFile) {
        return unix
    }
    return File(javaHome, "bin/java.exe")
}
