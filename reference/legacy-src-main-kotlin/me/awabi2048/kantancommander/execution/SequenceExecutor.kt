package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.model.CommandType
import me.awabi2048.kantancommander.data.model.ScriptCommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Registry
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SequenceExecutor(private val plugin: KantanCommanderPlugin) {

    private val executing: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun isExecuting(diskUUID: UUID): Boolean = diskUUID in executing

    fun execute(
        commands: List<ScriptCommand>,
        origin: Location,
        player: Player?,
        scriptUUID: UUID = UUID.randomUUID(),
        onComplete: (() -> Unit)? = null
    ) {
        if (commands.isEmpty()) return
        if (!executing.add(scriptUUID)) return

        val iterator = commands.listIterator()
        executeNextSafely(iterator, origin, player, scriptUUID, onComplete)
    }

    private fun executeNextSafely(
        iterator: ListIterator<ScriptCommand>,
        origin: Location,
        player: Player?,
        scriptUUID: UUID,
        onComplete: (() -> Unit)?
    ) {
        try {
            executeNext(iterator, origin, player, scriptUUID, onComplete)
        } catch (ex: Exception) {
            executing.remove(scriptUUID)
            plugin.logger.warning("Kantan Commander sequence failed: ${ex.message}")
            ex.printStackTrace()
        }
    }

    private fun executeNext(
        iterator: ListIterator<ScriptCommand>,
        origin: Location,
        player: Player?,
        scriptUUID: UUID,
        onComplete: (() -> Unit)?
    ) {
        if (!iterator.hasNext()) {
            executing.remove(scriptUUID)
            runCatching { onComplete?.invoke() }.onFailure { plugin.logger.warning("Kantan Commander completion callback failed: ${it.message}") }
            return
        }

        val cmd = iterator.next()

        @Suppress("DuplicatedCode")
        fun proceed() {
            executeNextSafely(iterator, origin, player, scriptUUID, onComplete)
        }

        when (cmd.type) {
            CommandType.SOUND -> {
                val soundKey = org.bukkit.NamespacedKey.fromString(cmd.getString("sound", "minecraft:block.note_block.pling")) ?: org.bukkit.NamespacedKey.fromString("minecraft:block.note_block.pling")!!
                val sound = Registry.SOUND_EVENT.get(soundKey)
                if (sound == null) {
                    proceed(); return@executeNext
                }
                val volume = cmd.getFloat("volume", 1.0f)
                val pitch = cmd.getFloat("pitch", 1.0f)
                val category = try {
                    SoundCategory.valueOf(cmd.getString("category", "MASTER").uppercase())
                } catch (_: Exception) {
                    SoundCategory.MASTER
                }
                origin.world.playSound(origin, sound, category, volume, pitch)
                proceed()
            }

            CommandType.MESSAGE -> {
                val text = cmd.getString("text", "")
                val target = cmd.getString("target", "nearby")
                val players = resolveTargets(origin, player, target)
                players.forEach { it.sendMessage(Component.text(text)) }
                proceed()
            }

            CommandType.PARTICLE -> {
                val particle = try {
                    Particle.valueOf(cmd.getString("particle", "FLAME").uppercase())
                } catch (_: Exception) {
                    Particle.FLAME
                }
                val count = cmd.getInt("count", 10)
                val speed = cmd.getDouble("speed", 0.1)
                val offsetX = cmd.getDouble("offsetX", 1.0)
                val offsetY = cmd.getDouble("offsetY", 1.0)
                val offsetZ = cmd.getDouble("offsetZ", 1.0)
                origin.world.spawnParticle(particle, origin, count, offsetX, offsetY, offsetZ, speed)
                proceed()
            }

            CommandType.WAIT -> {
                val ticks = cmd.getInt("ticks", 20).coerceAtMost(20 * 60 * 5) // max 5 min
                if (ticks <= 0) {
                    proceed()
                } else {
                    object : BukkitRunnable() {
                        override fun run() { proceed() }
                    }.runTaskLater(plugin, ticks.toLong())
                }
            }

            CommandType.TITLE -> {
                val titleText = cmd.getString("title", "")
                val subtitleText = cmd.getString("subtitle", "")
                val fadeIn = cmd.getInt("fadeIn", 10)
                val stay = cmd.getInt("stay", 40)
                val fadeOut = cmd.getInt("fadeOut", 10)
                val title = Title.title(
                    Component.text(titleText),
                    Component.text(subtitleText),
                    Title.Times.times(
                        java.time.Duration.ofMillis(fadeIn * 50L),
                        java.time.Duration.ofMillis(stay * 50L),
                        java.time.Duration.ofMillis(fadeOut * 50L)
                    )
                )
                val targets = origin.world.players.filter { it.location.distance(origin) <= 32.0 }
                targets.forEach { it.showTitle(title) }
                proceed()
            }

            CommandType.ACTIONBAR -> {
                val text = cmd.getString("text", "")
                val targets = origin.world.players.filter { it.location.distance(origin) <= 32.0 }
                targets.forEach { it.sendActionBar(Component.text(text)) }
                proceed()
            }

            CommandType.EFFECT -> {
                val effectName = cmd.getString("effect", "minecraft:speed")
                val effectKey = org.bukkit.NamespacedKey.fromString(effectName)
                val effect = if (effectKey != null) Registry.EFFECT.get(effectKey) else null
                val duration = cmd.getInt("duration", 100).coerceAtMost(20 * 60 * 10) // max 10 min
                val amplifier = cmd.getInt("amplifier", 0).coerceIn(0, 255)
                if (effect != null) {
                    val targets = origin.world.players.filter { it.location.distance(origin) <= 16.0 }
                    targets.forEach { it.addPotionEffect(org.bukkit.potion.PotionEffect(effect, duration, amplifier)) }
                }
                proceed()
            }

        }
    }

    private fun resolveTargets(origin: Location, player: Player?, target: String): List<Player> = when (target) {
        "self" -> if (player != null) listOf(player) else emptyList()
        "nearby" -> origin.world.players.filter { it.location.distance(origin) <= 32.0 }
        // 全体告知はKantan Commanderの責務外。古い "all" 指定も近距離に丸める。
        else -> origin.world.players.filter { it.location.distance(origin) <= 32.0 }
    }
}
