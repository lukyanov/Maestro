package ios

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import javax.imageio.ImageIO

/**
 * Exercises [FoldableSimulator]'s read-backs and screenshot against a real foldable simulator, in whatever
 * posture and orientation it is in: waiting for the state it is already in returns, waiting for one it is
 * not in fails. Nothing moves the device, so no XCTest runner is involved.
 *
 * Skipped unless MAESTRO_TEST_SIMULATOR_UDID names a booted iPhone Duo simulator (Xcode 27.1+):
 *
 *   MAESTRO_TEST_SIMULATOR_UDID=<udid> ./gradlew :maestro-ios:test --tests '*FoldableSimulatorSimulatorTest'
 */
@EnabledIfEnvironmentVariable(named = "MAESTRO_TEST_SIMULATOR_UDID", matches = ".+")
class FoldableSimulatorSimulatorTest {

    private val simulator = checkNotNull(FoldableSimulator.of(System.getenv("MAESTRO_TEST_SIMULATOR_UDID"))) {
        "MAESTRO_TEST_SIMULATOR_UDID must name a foldable simulator"
    }

    @Test
    fun `waiting for the posture the device is in returns, and for the other one fails`() {
        val open = checkNotNull(simulator.innerScreenLit())

        simulator.awaitFold(open)
        val error = assertThrows(IOSDeviceErrors.StateNotReached::class.java) {
            simulator.awaitFold(!open)
        }
        assertThat(error.errorMessage).contains(if (open) "cover screen" else "inner screen")
    }

    @Test
    fun `waiting for the orientation the device is in returns, and for another one fails`() {
        val current = checkNotNull(simulator.currentOrientation())
        val other = if (current == "portrait") "landscapeLeft" else "portrait"

        simulator.awaitOrientation(if (current == "portraitUpsideDown") "upsideDown" else current)
        val error = assertThrows(IOSDeviceErrors.StateNotReached::class.java) {
            simulator.awaitOrientation(other)
        }
        assertThat(error.errorMessage).contains("reports orientation $current")
    }

    @Test
    fun `the screenshot is the lit screen at its full size`() {
        val inner = checkNotNull(simulator.innerScreenLit())

        val image = ImageIO.read(checkNotNull(simulator.screenshot()).inputStream())

        // iPhone Duo panels, in either orientation.
        val expected = if (inner) setOf(2007, 2853) else setOf(1398, 2034)
        assertThat(setOf(image.width, image.height)).isEqualTo(expected)
    }

    @Test
    fun `an id that is not a simulator is not a foldable`() {
        assertThat(FoldableSimulator.of("00000000-0000-0000-0000-000000000000")).isNull()
    }
}
