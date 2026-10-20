package ai.opencode.mobile.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.TimeUnit

/** Port + facts the WebView needs once the engine answers on loopback. */
data class EngineRuntime(val port: Int)

/**
 * A runner owns the engine lifecycle. Two implementations across the plan:
 *  - [MuslBinaryEngine]  Track A (MVP): exec the Bun-compiled static musl binary.
 *  - [NodeBundleEngine]  Track B (v1.0): bionic Node + engine JS bundle (PLAN.md §4).
 */
interface EngineRunner {
    suspend fun start(): EngineRuntime
    fun stop()
}

/**
 * Track A: the engine is a single Bun-compiled binary (upstream target linux-arm64-musl,
 * built in CI) dropped into the APK as `jniLibs/arm64-v8a/libopencode.so`. Because it is
 * named lib*.so and the app sets useLegacyPackaging=true, Android extracts it with the
 * exec bit into nativeLibraryDir — where exec() is allowed.
 */
class MuslBinaryEngine(private val context: Context) : EngineRunner {

    private var process: Process? = null

    override suspend fun start(): EngineRuntime = withContext(Dispatchers.IO) {
        val libDir = context.applicationInfo.nativeLibraryDir
        val binary = File(libDir, "libopencode.so")
        check(binary.exists()) {
            "Missing engine binary ${binary.absolutePath} — engine CI artifact not embedded (see jniLibs README)."
        }

        // Ephemeral loopback port; never 0.0.0.0.
        val port = ServerSocket(0).use { it.localPort }

        val filesDir = context.filesDir.absolutePath
        val env = System.getenv().filterKeys { it !in SCRUBBED_ENV_KEYS }.toMutableMap()
        env["HOME"] = filesDir                // engine writes config/auth under app storage
        env["XDG_CONFIG_HOME"] = "$filesDir/.config"
        env["XDG_DATA_HOME"] = "$filesDir/.local/share"
        env["TMPDIR"] = context.cacheDir.absolutePath
        env["PATH"] = "$libDir:/system/bin"
        env["LD_LIBRARY_PATH"] = libDir

        // TODO(M1): verify flags against the pinned upstream version (serve.ts) and
        // capture the URL the engine logs on stdout for parity checking.
        process = ProcessBuilder(
            binary.absolutePath,
            "serve",
            "--port", port.toString(),
            "--hostname", "127.0.0.1",
        ).apply {
            directory(context.filesDir)
            redirectErrorStream(false)
            environment().clear()
            environment().putAll(env)
        }.start()

        awaitHttpReady(port, timeoutSec = 30)
        EngineRuntime(port)
    }

    override fun stop() {
        process?.destroy()
        process = null
    }

    private suspend fun awaitHttpReady(port: Int, timeoutSec: Long): Unit = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSec)
        while (System.currentTimeMillis() < deadline) {
            if (process?.isAlive != true) error("Engine process exited during startup")
            try {
                (URL("http://127.0.0.1:$port/").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 1000
                    readTimeout = 1000
                    requestMethod = "GET"
                    val ok = responseCode in 200..299
                    disconnect()
                    if (ok) return@withContext
                }
            } catch (_: java.io.IOException) {
                // engine not up yet
            }
            Thread.sleep(250)
        }
        error("Engine did not become ready within ${timeoutSec}s")
    }

    private companion object {
        // Prevent leaking host environment into the engine (adb sessions etc.).
        val SCRUBBED_ENV_KEYS = setOf(
            "HOME", "PATH", "TMPDIR", "TMP", "LD_LIBRARY_PATH", "LD_PRELOAD", "CLASSPATH",
            "JAVA_HOME", "PYTHONHOME", "PYTHONPATH", "SHELL", "USER", "LOGNAME",
        )
    }
}

/**
 * Track B placeholder: run the engine JS bundle on a bionic Node runtime shipped as
 * libnode.so. Implementation = PLAN.md §4 Track B (not started).
 */
class NodeBundleEngine(private val context: Context) : EngineRunner {
    override suspend fun start(): EngineRuntime {
        error("Track B (Node-bundle engine) not implemented yet — see PLAN.md §4")
    }

    override fun stop() = Unit
}
