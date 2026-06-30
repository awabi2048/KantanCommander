package me.awabi2048.kantancommander.data.model

import java.util.UUID

data class BlockPlacement(
    val worldName: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val diskUUID: UUID,
    val displayEntityUUID: UUID
) {
    fun locationKey(): String = "$worldName,$x,$y,$z"
}
