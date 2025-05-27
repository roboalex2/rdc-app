package at.roboalex2.rdc.model

data class SettingsState(
    var tabIndex: Int = 1,
    val numbers: List<NumberItem> = listOf(
        NumberItem("Loading...", emptyList())
    )
)