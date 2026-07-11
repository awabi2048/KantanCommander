package me.awabi2048.kantancommander.data.model

import java.util.UUID

data class DiskScript(
    val uuid: UUID,
    var name: String,
    val creator: UUID,
    val createdAt: Long = System.currentTimeMillis(),
    val commands: MutableList<ScriptCommand> = mutableListOf(),
    var triggerType: TriggerType = TriggerType.REDSTONE_EDGE
)

enum class TriggerType(val id: String, val displayNameKey: String) {
    REDSTONE_EDGE("edge", "trigger.edge"),
    REDSTONE_RISING("rising", "trigger.rising");

    companion object {
        fun fromId(id: String): TriggerType =
            entries.firstOrNull { it.id == id } ?: REDSTONE_EDGE
    }
}
