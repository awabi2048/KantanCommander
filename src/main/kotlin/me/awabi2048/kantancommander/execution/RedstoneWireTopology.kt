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
 * 拡張コマンドブロックに隣接するレッドストーンダストの形状だけを補正します。
 *
 * 実体はガラスのため、バニラのRedStoneWireBlockは拡張ブロックへ接続しません。
 * そのため、拡張ブロックに隣接するダストだけを例外扱いし、それ以外のダストは
 * バニラの物理更新へ任せます。現在のBlockDataを部分的に変更するだけでは以前の
 * 接続が残るため、対象ダストの4面すべてを書き戻して余分な接続を明示的に消します。
 */
internal class RedstoneWireTopology(
    private val isExtendedCommandBlock: (Block) -> Boolean,
) {
    /**
     * 指定位置の周囲から候補を収集し、拡張ブロックに隣接するダストだけを処理します。
     * 候補を広めに収集するのは、拡張ブロックの設置・撤去や段差のある配置でも、
     * 既存ダストの特殊接続を取りこぼさないためです。実際の書き換え対象は
     * 拡張ブロックに隣接するダストに限定されます。
     */
    fun refreshAround(center: Block) {
        val candidates = linkedMapOf<String, Block>()
        fun addCandidate(block: Block) {
            if (block.type != Material.REDSTONE_WIRE) return
            candidates.putIfAbsent(blockKey(block), block)
        }

        // 上下の段差も含めて調べます。中心がダストでない設置・物理更新でも、
        // この近傍走査で拡張ブロックに隣接する既存ダストを拾います。
        val seedFaces = listOf(BlockFace.UP, BlockFace.DOWN) + HORIZONTAL_FACES
        val seeds = buildList {
            add(center)
            seedFaces.forEach { add(center.getRelative(it)) }
        }
        seeds.forEach { seed ->
            addCandidate(seed)
            HORIZONTAL_FACES.forEach { face -> addCandidate(seed.getRelative(face)) }
        }
        candidates.values.forEach(::recalculateExtended)
    }

    /**
     * 拡張ブロック撤去後に、直前まで特殊接続だったダストだけをバニラ形状へ戻します。
     * この経路は通常の接続処理とは分離し、撤去対象に直接隣接する4方向だけを対象に
     * します。別の拡張ブロックにも隣接している場合は、その特殊接続を維持します。
     */
    fun restoreAfterExtendedRemoval(center: Block) {
        HORIZONTAL_FACES
            .map(center::getRelative)
            .filter { it.type == Material.REDSTONE_WIRE }
            .forEach { wireBlock ->
                if (extendedTargetFaces(wireBlock).isEmpty()) {
                    recalculateVanilla(wireBlock)
                } else {
                    recalculateExtended(wireBlock)
                }
            }
    }

    private fun recalculateExtended(wireBlock: Block) {
        val wire = wireBlock.blockData as? RedstoneWire ?: return
        val extendedTargetFaces = extendedTargetFaces(wireBlock)
        // 通常のダストへ介入すると、バニラが管理する接続形状を上書きしてしまいます。
        // 拡張ブロックに隣接しているダストだけを、このクラスの処理対象にします。
        if (extendedTargetFaces.isEmpty()) return

        val vanillaConnections = HORIZONTAL_FACES.associateWith { face ->
            vanillaConnection(wireBlock, face)
        }
        val adjacentDustFaces = HORIZONTAL_FACES
            .filter { face -> wireBlock.getRelative(face).type == Material.REDSTONE_WIRE }
            .toSet()
        val desired = resolveHorizontalConnections(
            vanillaConnections = vanillaConnections,
            extendedTargetFaces = extendedTargetFaces,
            adjacentDustFaces = adjacentDustFaces,
        )

        applyConnections(wireBlock, wire, desired)
    }

    private fun recalculateVanilla(wireBlock: Block) {
        val wire = wireBlock.blockData as? RedstoneWire ?: return
        val vanillaConnections = HORIZONTAL_FACES.associateWith { face ->
            vanillaConnection(wireBlock, face)
        }
        // 拡張接続を持っていたダストは、撤去後も現行のBlockDataを初期状態として
        // RedStoneWireBlock#getConnectionStateが計算します。ドット状態だった場合だけ、
        // 接続先がない結果をドットのまま維持します。
        applyConnections(
            wireBlock,
            wire,
            completeVanillaConnections(vanillaConnections, isDot(wire)),
        )
    }

    private fun applyConnections(
        wireBlock: Block,
        wire: RedstoneWire,
        desired: Map<BlockFace, RedstoneWire.Connection>,
    ) {
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
         *
         * 26.1.2のRedStoneWireBlockは、4方向に接続先がない配置時にcrossStateを基準
         * として形状を確定します。拡張ブロックに隣接し、かつ4方向に別のダストがない
         * 場合もその十字形を維持します。別のダストがある場合は、拡張ブロック側の
         * 面だけを追加し、残りはバニラと同じ軸補完規則で決めます。
         * 4面すべてを返すことで、現在のBlockDataに残った余分な接続を持ち越しません。
         */
        internal fun resolveHorizontalConnections(
            vanillaConnections: Map<BlockFace, RedstoneWire.Connection>,
            extendedTargetFaces: Set<BlockFace>,
            adjacentDustFaces: Set<BlockFace> = emptySet(),
        ): Map<BlockFace, RedstoneWire.Connection> {
            if (extendedTargetFaces.isEmpty()) {
                return HORIZONTAL_FACES.associateWith { face ->
                    vanillaConnections[face] ?: RedstoneWire.Connection.NONE
                }
            }

            // 別のダストが4方向にない場合は、26.1.2の配置時仕様に合わせて十字形にします。
            if (adjacentDustFaces.isEmpty()) {
                return HORIZONTAL_FACES.associateWith { RedstoneWire.Connection.SIDE }
            }

            val resolved = HORIZONTAL_FACES.associateWithTo(linkedMapOf()) { face ->
                if (face in extendedTargetFaces) {
                    RedstoneWire.Connection.SIDE
                } else {
                    vanillaConnections[face] ?: RedstoneWire.Connection.NONE
                }
            }

            return completeVanillaConnections(resolved, wasDot = false)
        }

        private fun completeVanillaConnections(
            vanillaConnections: Map<BlockFace, RedstoneWire.Connection>,
            wasDot: Boolean,
        ): Map<BlockFace, RedstoneWire.Connection> {
            val resolved = HORIZONTAL_FACES.associateWithTo(linkedMapOf()) { face ->
                vanillaConnections[face] ?: RedstoneWire.Connection.NONE
            }
            if (wasDot && resolved.values.none(::isConnected)) {
                return resolved
            }

            // RedStoneWireBlock#getConnectionStateと同じく、片軸だけに接続がある場合は
            // その軸の反対側を補完します。両軸に接続があるL字形や、既に線になって
            // いる形状は変更しません。接続先がない非ドット状態は十字形になります。
            val northSouthConnected = isConnected(resolved[BlockFace.NORTH]) ||
                isConnected(resolved[BlockFace.SOUTH])
            val eastWestConnected = isConnected(resolved[BlockFace.EAST]) ||
                isConnected(resolved[BlockFace.WEST])
            if (!northSouthConnected) {
                if (!isConnected(resolved[BlockFace.WEST])) {
                    resolved[BlockFace.WEST] = RedstoneWire.Connection.SIDE
                }
                if (!isConnected(resolved[BlockFace.EAST])) {
                    resolved[BlockFace.EAST] = RedstoneWire.Connection.SIDE
                }
            }
            if (!eastWestConnected) {
                if (!isConnected(resolved[BlockFace.NORTH])) {
                    resolved[BlockFace.NORTH] = RedstoneWire.Connection.SIDE
                }
                if (!isConnected(resolved[BlockFace.SOUTH])) {
                    resolved[BlockFace.SOUTH] = RedstoneWire.Connection.SIDE
                }
            }
            return resolved
        }

        private fun isConnected(connection: RedstoneWire.Connection?): Boolean =
            connection != null && connection != RedstoneWire.Connection.NONE

        private fun isDot(wire: RedstoneWire): Boolean =
            HORIZONTAL_FACES.all { face -> wire.getFace(face) == RedstoneWire.Connection.NONE }
    }

    private fun extendedTargetFaces(wireBlock: Block): Set<BlockFace> = HORIZONTAL_FACES
        .filter { face -> isExtendedCommandBlock(wireBlock.getRelative(face)) }
        .toSet()
}
