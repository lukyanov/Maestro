package ios

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Confirms that a foldable simulator has reached the posture or orientation it was sent to, and captures
 * the screen it has lit.
 *
 * The runner moves the device with Device Hub's events; nothing inside the simulator reports the result
 * reliably, so it is read back here from `devicectl`, which does. Neither read-back can tell a half-open
 * device from a flat one: both light the inner screen.
 */
class FoldableSimulator private constructor(private val deviceId: String, private val ports: ScreenPorts) {

    /** The `simctl io` ports of the two built-in screens. */
    data class ScreenPorts(val inner: String, val cover: String)

    /**
     * The lit screen as a PNG, at its native size and upright, or null when devicectl cannot tell which
     * screen is lit or simctl cannot capture it. Taken from the host, because XCUIScreen's capture of the
     * iPhone Duo's inner screen comes out a pixel short each way: 2852×2006 of its 2853×2007.
     */
    fun screenshot(): ByteArray? {
        val port = when (innerScreenLit()) {
            true -> ports.inner
            false -> ports.cover
            null -> return null
        }
        val file = File.createTempFile("maestro-foldable-", ".png")
        return try {
            runCatching {
                run(listOf("xcrun", "simctl", "io", deviceId, "screenshot", "--display=$port", file.absolutePath))
                file.readBytes()
            }
                .onFailure { logger.warn("simctl io screenshot failed: ${it.message}") }
                .getOrNull()
        } finally {
            file.delete()
        }
    }

    /** null when devicectl cannot be read. */
    fun innerScreenLit(): Boolean? = devicectl("device", "info", "displays")?.let(::isInnerScreenLit)

    /** devicectl's name for the orientation, or null when it cannot be read. */
    fun currentOrientation(): String? = devicectl("device", "orientation", "get")?.let(::deviceOrientation)

    fun awaitFold(open: Boolean) {
        val reached = poll { innerScreenLit() == open }
        if (!reached) {
            val screen = if (open) "inner" else "cover"
            throw IOSDeviceErrors.StateNotReached(
                "Device $deviceId did not light its $screen screen within ${TIMEOUT_MS / 1000} s"
            )
        }
    }

    fun awaitOrientation(orientation: String) {
        val expected = devicectlOrientation(orientation)
        var current: String? = null
        val reached = poll {
            current = currentOrientation()
            current == expected
        }
        if (!reached) {
            throw IOSDeviceErrors.StateNotReached(
                "Device $deviceId reports orientation $current, ${TIMEOUT_MS / 1000} s after being turned to $expected"
            )
        }
    }

    private fun poll(reached: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS)
        while (true) {
            if (reached()) return true
            if (System.nanoTime() >= deadline) return false
            Thread.sleep(POLL_MS)
        }
    }

    /** null when devicectl fails, which a poll treats as "not yet". */
    private fun devicectl(vararg command: String): String? =
        runCatching { run(listOf("xcrun", "devicectl") + command + listOf("-d", deviceId, "-q", "-j", "-")) }
            .onFailure { logger.warn("devicectl ${command.joinToString(" ")} failed: ${it.message}") }
            .getOrNull()

    companion object {
        private val logger = LoggerFactory.getLogger(FoldableSimulator::class.java)
        private val mapper = jacksonObjectMapper()
        private const val TIMEOUT_MS = 10_000L
        private const val POLL_MS = 250L

        /** The device as a foldable simulator, or null when it has a single built-in screen or is not a simulator. */
        fun of(deviceId: String): FoldableSimulator? {
            val enumerate = runCatching { run(listOf("xcrun", "simctl", "io", deviceId, "enumerate")) }
                .onFailure { logger.info("Not treating $deviceId as a foldable simulator: ${it.message}") }
                .getOrNull()
                ?: return null
            return screenPorts(enumerate)?.let { FoldableSimulator(deviceId, it) }
        }

        /**
         * The inner screen is the largest built-in display, the cover screen the smallest; null with fewer
         * than two. `simctl io enumerate` lists external outputs (CarPlay, TV out) as display class 1.
         */
        internal fun screenPorts(enumerate: String): ScreenPorts? {
            val displays = builtInDisplays(enumerate).sortedBy { it.second }
            if (displays.size < 2) return null
            return ScreenPorts(inner = displays.last().first, cover = displays.first().first)
        }

        /** Each built-in display's port and area in pixels. */
        private fun builtInDisplays(enumerate: String): List<Pair<String, Long>> =
            enumerate.split("Port:").drop(1)
                .filter { Regex("""^\s*Display class: 0\s*$""", RegexOption.MULTILINE).containsMatchIn(it) }
                .mapNotNull { port ->
                    val uuid = Regex("""UUID: (\S+)""").find(port)?.groupValues?.get(1) ?: return@mapNotNull null
                    val width = Regex("""Default width: (\d+)""").find(port)?.groupValues?.get(1)?.toLong() ?: 0
                    val height = Regex("""Default height: (\d+)""").find(port)?.groupValues?.get(1)?.toLong() ?: 0
                    uuid to width * height
                }

        internal fun deviceOrientation(json: String): String? =
            mapper.readTree(json).path("result").path("deviceOrientation").textValue()

        internal fun isInnerScreenLit(json: String): Boolean =
            mapper.readTree(json).path("result").path("displays")
                .any { display: JsonNode -> !display.path("primary").asBoolean() && display.path("active").asBoolean() }

        /** Maestro sends `upsideDown`; devicectl reports it as `portraitUpsideDown`. */
        internal fun devicectlOrientation(orientation: String): String =
            if (orientation == "upsideDown") "portraitUpsideDown" else orientation

        /**
         * The hinge angle for a posture, by Maestro's name for it. Half open is a book in portrait, opened
         * to 127° as Device Hub's own book preset is, and a tabletop in landscape, opened to 115° like a
         * laptop. An orientation that could not be read counts as portrait. [orientation] is in either
         * Maestro's or devicectl's spelling.
         */
        fun hingeDegrees(posture: String, orientation: String?): Double = when (posture) {
            "closed" -> 0.0
            "flat" -> 180.0
            "halfFold" -> if (orientation?.startsWith("landscape") == true) TABLETOP_DEGREES else BOOK_DEGREES
            else -> throw IllegalArgumentException("Unknown fold posture: $posture")
        }

        private const val BOOK_DEGREES = 127.0
        private const val TABLETOP_DEGREES = 115.0

        private fun run(command: List<String>): String {
            val process = ProcessBuilder(command).redirectErrorStream(false).start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                val error = process.errorStream.bufferedReader().readText()
                throw IllegalStateException("${command.take(4).joinToString(" ")} failed: $error")
            }
            return output
        }
    }
}
