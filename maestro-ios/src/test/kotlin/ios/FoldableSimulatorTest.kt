package ios

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** The outputs below are trimmed from an iPhone Duo simulator (Xcode 27.1, iOS 27.1). */
class FoldableSimulatorTest {

    @Test
    fun `counts the built-in displays, not the external ones`() {
        val enumerate = """
            Port:
                UUID: DB68575E-9A7D-4F9F-8B28-416C039E194B
                Class: Unknown
                Port Identifier: com.apple.display.captureservice

            Port:
                UUID: AB99997C-07A9-4C40-B60F-AB1D7D0DEEC9
                Class: Display
                Display class: 0
                Default width: 2007
                Default height: 2853

            Port:
                UUID: 55010758-3A38-434C-90B9-E6956D7E83A6
                Class: Display
                Display class: 0
                Default width: 1398
                Default height: 2034

            Port:
                UUID: 632CED15-1B20-4C5C-BF3C-7CD794B9140B
                Class: Display
                Display class: 1
                Default width: 7680
                Default height: 4320
        """.trimIndent()

        assertThat(FoldableSimulator.builtInDisplayCount(enumerate)).isEqualTo(2)
    }

    @Test
    fun `reads the device orientation`() {
        val json = """
            {"info": {"outcome": "success"},
             "result": {"deviceOrientation": "landscapeLeft", "deviceOrientationNonFlat": "landscapeLeft"}}
        """.trimIndent()

        assertThat(FoldableSimulator.deviceOrientation(json)).isEqualTo("landscapeLeft")
    }

    @Test
    fun `the inner screen is lit when a display other than the primary is active`() {
        val open = """
            {"result": {"displays": [
              {"displayId": 1, "name": "LCD", "primary": true, "active": false, "backlightState": "off"},
              {"displayId": 3, "name": "LCD-1", "primary": false, "active": true, "backlightState": "activeOn"}
            ]}}
        """.trimIndent()
        val closed = """
            {"result": {"displays": [
              {"displayId": 1, "name": "LCD", "primary": true, "active": true, "backlightState": "activeOn"},
              {"displayId": 3, "name": "LCD-1", "primary": false, "active": false, "backlightState": "off"}
            ]}}
        """.trimIndent()

        assertThat(FoldableSimulator.isInnerScreenLit(open)).isTrue()
        assertThat(FoldableSimulator.isInnerScreenLit(closed)).isFalse()
    }

    @Test
    fun `half open is a book at 127 degrees in portrait and a tabletop at 115 in landscape`() {
        assertThat(FoldableSimulator.hingeDegrees("halfFold", "portrait")).isEqualTo(127.0)
        assertThat(FoldableSimulator.hingeDegrees("halfFold", "upsideDown")).isEqualTo(127.0)
        assertThat(FoldableSimulator.hingeDegrees("halfFold", "portraitUpsideDown")).isEqualTo(127.0)
        assertThat(FoldableSimulator.hingeDegrees("halfFold", "landscapeLeft")).isEqualTo(115.0)
        assertThat(FoldableSimulator.hingeDegrees("halfFold", "landscapeRight")).isEqualTo(115.0)
    }

    @Test
    fun `half open with the orientation unread is a book`() {
        assertThat(FoldableSimulator.hingeDegrees("halfFold", null)).isEqualTo(127.0)
    }

    @Test
    fun `closed and flat take the same angle in every orientation`() {
        for (orientation in listOf("portrait", "landscapeLeft", null)) {
            assertThat(FoldableSimulator.hingeDegrees("closed", orientation)).isEqualTo(0.0)
            assertThat(FoldableSimulator.hingeDegrees("flat", orientation)).isEqualTo(180.0)
        }
    }

    @Test
    fun `maps Maestro orientations to the names devicectl reports`() {
        assertThat(FoldableSimulator.devicectlOrientation("portrait")).isEqualTo("portrait")
        assertThat(FoldableSimulator.devicectlOrientation("landscapeLeft")).isEqualTo("landscapeLeft")
        assertThat(FoldableSimulator.devicectlOrientation("landscapeRight")).isEqualTo("landscapeRight")
        assertThat(FoldableSimulator.devicectlOrientation("upsideDown")).isEqualTo("portraitUpsideDown")
    }
}
