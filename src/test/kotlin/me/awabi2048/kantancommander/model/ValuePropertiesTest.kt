package me.awabi2048.kantancommander.model

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.UUID

/**
 * issue #3 の詳細値取得テストです。
 *
 * 型安全なadapterの一覧・結果型・取得不能条件を固定します。Bukkit実体を
 * 必要とするENTITY系は動的プロキシの偽装実体で検証し、サーバーを要求しません。
 */
class ValuePropertiesTest {
    @Test
    fun `registry exposes the supported property list`() {
        assertEquals(
            setOf("name", "type", "health", "location", "gamemode", "distance", "has_item"),
            ValueProperties.forSource(ValueType.ENTITY).map { it.id }.toSet(),
        )
        assertEquals(
            setOf("x", "y", "z", "yaw", "pitch", "distance", "block", "redstone_power"),
            ValueProperties.forSource(ValueType.LOCATION).map { it.id }.toSet(),
        )
        assertEquals(
            setOf("type", "amount"),
            ValueProperties.forSource(ValueType.ITEM).map { it.id }.toSet(),
        )
        assertEquals(listOf("type"), ValueProperties.forSource(ValueType.BLOCK).map { it.id })
        assertTrue(ValueProperties.forSource(ValueType.NUMBER).isEmpty())
        assertEquals(ValueType.NUMBER, ValueProperties.definition("health")?.result)
        assertEquals(ValueType.LOCATION, ValueProperties.definition("location")?.result)
        assertNull(ValueProperties.definition("unknown"))
        // 引数を取る詳細値は定義へ引数型を保持します。
        assertEquals(ValueType.LOCATION, ValueProperties.definition("distance")?.argType)
        assertEquals(ValueType.ITEM, ValueProperties.definition("has_item")?.argType)
        assertNull(ValueProperties.definition("health")?.argType)
    }

    @Test
    fun `location item and block properties resolve without a server`() {
        val location = TypedValue(ValueType.LOCATION, location = SavedLocation(1.0, 2.0, 3.0, 90f, 45f))
        assertEquals(1.0, ValueProperties.resolve(location, "x") { null }?.numberValue)
        assertEquals(2.0, ValueProperties.resolve(location, "y") { null }?.numberValue)
        assertEquals(3.0, ValueProperties.resolve(location, "z") { null }?.numberValue)
        assertEquals(90.0, ValueProperties.resolve(location, "yaw") { null }?.numberValue)
        assertEquals(45.0, ValueProperties.resolve(location, "pitch") { null }?.numberValue)

        // 座標成分は独立したベクトル値ではなく位置値から取得します。
        assertNull(ValueProperties.resolve(location, "unknown") { null })
        assertNull(ValueProperties.resolve(location, "amount") { null })
        assertNull(ValueProperties.resolve(location, "unknown") { null })

        val item = TypedValue(ValueType.ITEM, item = "STONE", amount = 3)
        assertEquals("STONE", ValueProperties.resolve(item, "type") { null }?.stringValue)
        assertEquals(3.0, ValueProperties.resolve(item, "amount") { null }?.numberValue)
        val single = TypedValue(ValueType.ITEM, item = "STONE")
        assertEquals(1.0, ValueProperties.resolve(single, "amount") { null }?.numberValue)

        val block = TypedValue(ValueType.BLOCK, block = "STONE")
        assertEquals("STONE", ValueProperties.resolve(block, "type") { null }?.stringValue)
    }

    @Test
    fun `entity properties resolve through the lookup`() {
        val id = UUID.randomUUID()
        val entity = fakeEntity("boss", EntityType.ZOMBIE, 15.5, id)
        val value = TypedValue(ValueType.ENTITY, entityId = id)
        val lookup: (UUID) -> Entity? = { if (it == id) entity else null }

        assertEquals("boss", ValueProperties.resolve(value, "name", entityLookup = lookup)?.stringValue)
        assertEquals("minecraft:zombie", ValueProperties.resolve(value, "type", entityLookup = lookup)?.stringValue)
        assertEquals(15.5, ValueProperties.resolve(value, "health", entityLookup = lookup)?.numberValue)
        assertEquals(10.0, ValueProperties.resolve(value, "location", entityLookup = lookup)?.location?.x)

        // 実体消失・未保持は解決不能として扱います。
        assertNull(ValueProperties.resolve(value, "name") { null })
        assertNull(ValueProperties.resolve(TypedValue(ValueType.ENTITY), "health", entityLookup = lookup))
        // 非生物に体力はありません。
        val markerId = UUID.randomUUID()
        val marker = fakeEntity("mark", EntityType.ARMOR_STAND, null, markerId)
        val markerValue = TypedValue(ValueType.ENTITY, entityId = markerId)
        assertNull(ValueProperties.resolve(markerValue, "health") { marker })
    }

    @Test
    fun `gamemode resolves only for players`() {
        val id = UUID.randomUUID()
        val player = fakePlayer(id, GameMode.CREATIVE)
        val value = TypedValue(ValueType.ENTITY, entityId = id)
        assertEquals("CREATIVE", ValueProperties.resolve(value, "gamemode") { player }?.stringValue)
        // 非プレイヤーにゲームモードはなく、既定値へ落とさず解決不能とします。
        val zombieId = UUID.randomUUID()
        val zombie = fakeEntity("z", EntityType.ZOMBIE, 1.0, zombieId)
        assertNull(
            ValueProperties.resolve(TypedValue(ValueType.ENTITY, entityId = zombieId), "gamemode") { zombie },
        )
    }

    @Test
    fun `distance resolves for entity and location sources`() {
        val id = UUID.randomUUID()
        val entity = fakeEntity("e", EntityType.ZOMBIE, 1.0, id)
        val source = TypedValue(ValueType.ENTITY, entityId = id)
        // 実体は (10,64,0) にいるため (13,68,0) までの距離は 5 です。
        val far = TypedValue(ValueType.LOCATION, location = SavedLocation(13.0, 68.0, 0.0))
        assertEquals(
            5.0,
            ValueProperties.resolve(source, "distance", arg = far) { entity }?.numberValue,
        )
        val origin = TypedValue(ValueType.LOCATION, location = SavedLocation(0.0, 0.0, 0.0))
        val to = TypedValue(ValueType.LOCATION, location = SavedLocation(3.0, 4.0, 0.0))
        assertEquals(
            5.0,
            ValueProperties.resolve(origin, "distance", arg = to) { null }?.numberValue,
        )
        // 引数の欠落・型不一致は解決不能として扱います。
        assertNull(ValueProperties.resolve(origin, "distance") { null })
        assertNull(
            ValueProperties.resolve(
                origin, "distance",
                arg = TypedValue(ValueType.NUMBER, numberValue = 1.0),
            ) { null },
        )
    }

    @Test
    fun `block and redstone reads use the injected environment`() {
        val location = TypedValue(ValueType.LOCATION, location = SavedLocation(1.0, 64.0, 2.0))
        // 環境未注入は既定値へ落とさず解決不能とします。
        assertNull(ValueProperties.resolve(location, "block") { null })
        assertNull(ValueProperties.resolve(location, "redstone_power") { null })
        val environment = ValueProperties.Environment(
            blockAt = { fakeBlock(Material.REDSTONE_BLOCK) },
            signalStrengthAt = { 12 },
        )
        assertEquals(
            "minecraft:redstone_block",
            ValueProperties.resolve(location, "block", environment = environment) { null }?.block,
        )
        assertEquals(
            12.0,
            ValueProperties.resolve(location, "redstone_power", environment = environment) { null }
                ?.numberValue,
        )
    }

    @Test
    fun `has_item delegates to the injected check`() {
        val id = UUID.randomUUID()
        val player = fakePlayer(id, GameMode.SURVIVAL)
        val source = TypedValue(ValueType.ENTITY, entityId = id)
        val item = TypedValue(ValueType.ITEM, item = "minecraft:diamond")
        val environment = ValueProperties.Environment(hasItem = { _, _ -> true })
        assertEquals(
            true,
            ValueProperties.resolve(source, "has_item", arg = item, environment = environment) { player }
                ?.booleanValue,
        )
        // 引数欠落・環境未注入は解決不能とします。
        assertNull(ValueProperties.resolve(source, "has_item", arg = item) { player })
        assertNull(ValueProperties.resolve(source, "has_item", environment = environment) { player })
    }

    private fun fakePlayer(id: UUID, gameMode: GameMode): Player {
        val handler = InvocationHandler { proxy, method, _ ->
            when (method.name) {
                "toString" -> "fakePlayer($id)"
                "hashCode" -> System.identityHashCode(proxy)
                "getName" -> "hero"
                "getType" -> EntityType.PLAYER
                "getUniqueId" -> id
                "getGameMode" -> gameMode
                "getLocation" -> Location(null, 10.0, 64.0, 0.0)
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(Player::class.java),
            handler,
        ) as Player
    }

    private fun fakeBlock(material: Material): Block = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(Block::class.java),
        InvocationHandler { proxy, method, _ ->
            when (method.name) {
                "toString" -> "fakeBlock($material)"
                "hashCode" -> System.identityHashCode(proxy)
                "getType" -> material
                else -> null
            }
        },
    ) as Block

    @Suppress("UNCHECKED_CAST")
    private fun fakeEntity(name: String, type: EntityType, health: Double?, id: UUID): Entity {
        val handler = InvocationHandler { proxy, method, _ ->
            when (method.name) {
                "toString" -> "fakeEntity($name)"
                "hashCode" -> System.identityHashCode(proxy)
                "getName" -> name
                "getType" -> type
                "getUniqueId" -> id
                "getHealth" -> health
                "getLocation" -> Location(null, 10.0, 64.0, 0.0)
                else -> null
            }
        }
        // 非生物は LivingEntity を実装しないため、体力解決は型で除外されます。
        val interfaces = if (health == null) {
            arrayOf(Entity::class.java)
        } else {
            arrayOf(Entity::class.java, LivingEntity::class.java)
        }
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            interfaces,
            handler,
        ) as Entity
    }
}
