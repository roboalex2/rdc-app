package at.roboalex2.rdc.model

data class CommandState(
    val commands: List<Command> = listOf(Command("", "Loading...", ""))
)