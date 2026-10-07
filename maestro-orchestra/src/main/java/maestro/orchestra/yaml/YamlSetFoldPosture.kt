package maestro.orchestra.yaml

import com.fasterxml.jackson.annotation.JsonCreator
import maestro.device.FoldPosture
import maestro.orchestra.yaml.schema.YamlValues

data class YamlSetFoldPosture(
    @YamlValues(FoldPosture::class, spelledBy = "yamlName")
    val posture: String,
    val label: String? = null,
    val optional: Boolean = false,
) {
    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun parse(@YamlValues(FoldPosture::class, spelledBy = "yamlName") posture: String) = YamlSetFoldPosture(
            posture = posture,
        )
    }
}
