package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gui.MenuRoute
import me.awabi2048.kantancommander.model.DiskPlacement
import java.util.UUID

/**
 * 編集画面群で失ってはならない状態を一つの値として扱います。
 *
 * 各サブ画面はこのセッションのpayloadを引き継ぎ、画面固有の値だけを追加します。
 */
data class EditorSession(
    val scriptId: UUID,
    val origin: MapPoint = MapPoint(0, 0),
    val placement: PlacementContext? = null,
) {
    data class PlacementContext(
        val world: String,
        val x: Int,
        val y: Int,
        val z: Int,
    )

    fun payload(): Map<String, String> = buildMap {
        put(SCRIPT_ID, scriptId.toString())
        put(ORIGIN_X, origin.x.coerceAtLeast(0).toString())
        put(ORIGIN_Y, origin.y.coerceAtLeast(0).toString())
        placement?.let {
            put(WORLD, it.world)
            put(X, it.x.toString())
            put(Y, it.y.toString())
            put(Z, it.z.toString())
        }
    }

    fun route(owner: String, id: String, extra: Map<String, String> = emptyMap()): MenuRoute =
        MenuRoute(owner, id, payload() + extra)

    fun withOrigin(origin: MapPoint): EditorSession = copy(
        origin = MapPoint(origin.x.coerceAtLeast(0), origin.y.coerceAtLeast(0)),
    )

    companion object {
        const val SCRIPT_ID = "scriptId"
        const val ORIGIN_X = "originX"
        const val ORIGIN_Y = "originY"
        const val WORLD = "world"
        const val X = "x"
        const val Y = "y"
        const val Z = "z"

        fun forScript(scriptId: UUID) = EditorSession(scriptId)

        fun forPlacement(placement: DiskPlacement) = EditorSession(
            scriptId = placement.scriptId,
            placement = PlacementContext(placement.world, placement.x, placement.y, placement.z),
        )

        fun from(route: MenuRoute): EditorSession? {
            val scriptId = route.payload[SCRIPT_ID]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return null
            val placement = route.payload[WORLD]?.let { world ->
                val x = route.payload[X]?.toIntOrNull() ?: return@let null
                val y = route.payload[Y]?.toIntOrNull() ?: return@let null
                val z = route.payload[Z]?.toIntOrNull() ?: return@let null
                PlacementContext(world, x, y, z)
            }
            return EditorSession(
                scriptId = scriptId,
                origin = MapPoint(
                    route.payload[ORIGIN_X]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                    route.payload[ORIGIN_Y]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                ),
                placement = placement,
            )
        }
    }
}
