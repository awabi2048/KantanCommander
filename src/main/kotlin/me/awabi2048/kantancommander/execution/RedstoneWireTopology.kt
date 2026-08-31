package me.awabi2048.kantancommander.execution

import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockSupport
import org.bukkit.block.data.Directional
import org.bukkit.block.data.type.RedstoneWire

private val HORIZONTAL_FACES = listOf(
    BlockFace.NORTH,
    BlockFace.SOUTH,
    BlockFace.EAST,
    BlockFace.WEST,
)

/**
 * 拡張コマンドブロック周辺のレッドストーンダスト形状を再構築します。
 *
 * 実体はガラスのため、バニラのRedStoneWireBlockは拡張ブロックへ接続しません。
 * その1面だけをKantanの管理対象に限って例外扱いし、残りの4面は現行サーバーの
 * バニラ接続規則に合わせて毎回計算します。現在のBlockDataを部分的に変更するだけ
 * では以前の接続が残るため、4面すべてを書き戻して余分な接続を明示的に消します。
 */
internal class RedstoneWireTopology(
    private val isExtendedCommandBlock: (Block) -> Boolean,
) {
    /** 指定位置の周囲にある、形状再計算が必要なダストを収集します。 */
    fun refreshAround(center: Block) {
        val candidates = linkedMapOf<String, Block>()
        fun addCandidate(block: Block) {
            if (block.type != Material.REDSTONE_WIRE) return
            candidates.putIfAbsent(blockKey(block), block)
        }

        // 上下の段差も含めて調べます。対象ブロックの撤去後は中心がAIRになるため、
        // 中心ブロック自体ではなく、この近傍走査で接続の残ったダストを拾います。
        val seedFaces = listOf(BlockFace.UP, BlockFace.DOWN) + HORIZONTAL_FACES
        val seeds = buildList {
            add(center)
            seedFaces.forEach { add(center.getRelative(it)) }
        }
        seeds.forEach { seed ->
            addCandidate(seed)
            HORIZONTAL_FACES.forEach { face -> addCandidate(seed.getRelative(face)) }
        }
        candidates.values.forEach(::recalculate)
    }

    private fun recalculate(wireBlock: Block) {
        val wire = wireBlock.blockData as? RedstoneWire ?: return
        val vanillaConnections = HORIZONTAL_FACES.associateWith { face ->
            vanillaConnection(wireBlock, face)
        }
        val extendedTargetFaces = HORIZONTAL_FACES
            .filter { face -> isExtendedCommandBlock(wireBlock.getRelative(face)) }
            .toSet()
        val desired = resolveHorizontalConnections(vanillaConnections, extendedTargetFaces)

        val corrected = wire.clone() as RedstoneWire
        var changed = false
        HORIZONTAL_FACES.forEach { face ->
            val connection = desired.getValue(face)
            if (corrected.getFace(face) == connection) return@forEach
            corrected.setFace(face, connection)
            changed = true
        }
        if (changed) {
            // バニラ物理に再びガラス側の接続をNONEへ戻されないよう、形状の確定は
            // 物理更新なしで行います。周辺の候補は同じ走査で明示的に再計算します。
            wireBlock.setBlockData(corrected, false)
        }
    }

    /**
     * 現行バニラのRedStoneWireBlock#getConnectingSide相当の水平面判定です。
     * 上段のワイヤーへ登るUP形状も、通常の配置で必要になる範囲を再現します。
     */
    private fun vanillaConnection(wire: Block, direction: BlockFace): RedstoneWire.Connection {
        val neighbor = wire.getRelative(direction)
        val canClimb = !isRedstoneConductor(wire.getRelative(BlockFace.UP))
        if (
            canClimb &&
            (Tag.TRAPDOORS.isTagged(neighbor.type) || canSupportWire(neighbor)) &&
            shouldConnectWithoutDirection(neighbor.getRelative(BlockFace.UP)) &&
            neighbor.blockData.isFaceSturdy(direction.oppositeFace, BlockSupport.FULL)
        ) {
            return RedstoneWire.Connection.UP
        }

        return if (
            shouldConnectTo(neighbor, direction) ||
            (!isRedstoneConductor(neighbor) &&
                shouldConnectWithoutDirection(neighbor.getRelative(BlockFace.DOWN)))
        ) {
            RedstoneWire.Connection.SIDE
        } else {
            RedstoneWire.Connection.NONE
        }
    }

    /**
     * NMSのisRedstoneConductorに相当する、Bukkit APIで取得できる遮蔽判定です。
     * ガラス・空気・ダストなどを導体扱いせず、フルブロック上だけでUP接続を
     * 許可するために使います。
     */
    private fun isRedstoneConductor(block: Block): Boolean = block.blockData.isOccluding

    private fun canSupportWire(block: Block): Boolean =
        block.type == Material.HOPPER ||
            block.blockData.isFaceSturdy(BlockFace.UP, BlockSupport.FULL)

    /** 引数なしのバニラshouldConnectToは、方向性のないレッドストーンダストだけを認めます。 */
    private fun shouldConnectWithoutDirection(block: Block): Boolean =
        block.type == Material.REDSTONE_WIRE

    /**
     * 同じ高さの隣接ブロックに対するバニラの接続先判定です。
     * リピーターは入出力の両端、オブザーバーは向いている面だけに接続します。
     */
    private fun shouldConnectTo(block: Block, direction: BlockFace): Boolean {
        if (block.type == Material.REDSTONE_WIRE) return true

        val facing = (block.blockData as? Directional)?.facing
        return when (block.type) {
            Material.REPEATER,
            Material.COMPARATOR,
            -> facing == direction || facing == direction.oppositeFace
            Material.OBSERVER -> facing == direction
            else -> isVanillaSignalSource(block)
        }
    }

    /**
     * 現行サーバーのRedStoneWireBlock#shouldConnectToが参照する信号源の集合です。
     * Powerable全体を一括採用すると、ドア・トラップドアなどの「電力を受けるだけの
     * ブロック」まで接続先になるため、バニラの信号源クラスに対応するものだけを
     * 明示しています。
     */
    private fun isVanillaSignalSource(block: Block): Boolean {
        val material = block.type
        return when {
            Tag.BUTTONS.isTagged(material) || Tag.PRESSURE_PLATES.isTagged(material) -> true
            Tag.LIGHTNING_RODS.isTagged(material) -> true
            material == Material.REDSTONE_BLOCK ||
            material == Material.REDSTONE_TORCH ||
            material == Material.REDSTONE_WALL_TORCH ||
            material == Material.LEVER ||
            material == Material.DAYLIGHT_DETECTOR ||
            material == Material.DETECTOR_RAIL ||
            material == Material.JUKEBOX ||
            material == Material.TRAPPED_CHEST ||
            material == Material.TARGET ||
            material == Material.TRIPWIRE_HOOK ||
            material == Material.SCULK_SENSOR ||
            material == Material.CALIBRATED_SCULK_SENSOR ||
            material == Material.LECTERN -> true
            else -> false
        }
    }

    private fun blockKey(block: Block): String =
        "${block.world.uid}:${block.x}:${block.y}:${block.z}"

    companion object {
        /**
         * バニラ再計算結果へ拡張ブロックの例外だけを重ねます。
         * 4面すべてを返すことで、現在のBlockDataに残った余分な接続を持ち越しません。
         */
        internal fun resolveHorizontalConnections(
            vanillaConnections: Map<BlockFace, RedstoneWire.Connection>,
            extendedTargetFaces: Set<BlockFace>,
        ): Map<BlockFace, RedstoneWire.Connection> = HORIZONTAL_FACES.associateWith { face ->
            if (face in extendedTargetFaces) {
                RedstoneWire.Connection.SIDE
            } else {
                vanillaConnections[face] ?: RedstoneWire.Connection.NONE
            }
        }
    }
}
