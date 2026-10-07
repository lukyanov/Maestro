package maestro.device

/**
 * How far a foldable is opened. Half open is a book in portrait and a tabletop in landscape; the
 * orientation is set separately, with `setOrientation`.
 */
enum class FoldPosture(val yamlName: String, val hingeDegrees: Double) {
    CLOSED("closed", 0.0),
    HALF_FOLD("halfFold", 90.0),
    FLAT("flat", 180.0);

    companion object {
        fun getByYamlName(name: String): FoldPosture? = entries.find { it.yamlName == name }
    }
}
