package maestro.device

/**
 * How far a foldable is opened. Half open is a book in portrait and a tabletop in landscape; the
 * orientation is set separately, with `setOrientation`. The driver picks the half-open angle.
 */
enum class FoldPosture(val yamlName: String) {
    CLOSED("closed"),
    HALF_FOLD("halfFold"),
    FLAT("flat");

    companion object {
        fun getByYamlName(name: String): FoldPosture? = entries.find { it.yamlName == name }
    }
}
