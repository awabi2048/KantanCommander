package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ScriptCommand
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration
import java.util.UUID

class SequenceExecutor(private val plugin: KantanCommanderPlugin) {
    private val running = mutableSetOf<UUID>()

    fun execute(scriptId: UUID, origin: Location, actor: Player? = null) {
        if (!running.add(scriptId)) return
        val script = plugin.scripts.load(scriptId) ?: return running.remove(scriptId).let {}
        runNext(script.commands.toList(), 0, origin, actor) {
            running.remove(scriptId)
        }
    }

    private fun runNext(commands: List<ScriptCommand>, index: Int, origin: Location, actor: Player?, done: () -> Unit) {
        if (index >= commands.size) {
            done()
            return
        }

        val command = commands[index]
        val waitTicks = executeCommand(command, origin, actor)
        object : BukkitRunnable() {
            override fun run() {
                runNext(commands, index + 1, origin, actor, done)
            }
        }.runTaskLater(plugin, waitTicks.coerceAtLeast(1L))
    }

    private fun executeCommand(command: ScriptCommand, origin: Location, actor: Player?): Long = when (command.type) {
        CommandType.SOUND -> {
            val sound = registryValue(Registry.SOUNDS, command.string("sound", "minecraft:block.note_block.pling"))
            val category = runCatching { SoundCategory.valueOf(command.string("category", "MASTER")) }.getOrDefault(SoundCategory.MASTER)
            if (sound != null) origin.world.playSound(origin, sound, category, command.double("volume", 1.0).toFloat(), command.double("pitch", 1.0).toFloat())
            1L
        }
        CommandType.MESSAGE -> {
            targets(origin, actor, command.string("target", "nearby")).forEach { it.sendMessage(command.string("text")) }
            1L
        }
        CommandType.PARTICLE -> {
            val particle = registryValue(Registry.PARTICLE_TYPE, command.string("particle", "minecraft:happy_villager"))
            if (particle != null) {
                origin.world.spawnParticle(
                    particle,
                    origin,
                    command.int("count", 8),
                    command.double("offsetX", 0.5),
                    command.double("offsetY", 0.5),
                    command.double("offsetZ", 0.5),
                    command.double("speed", 0.0)
                )
            }
            1L
        }
        CommandType.WAIT -> command.int("ticks", 20).toLong()
        CommandType.TITLE -> {
            targets(origin, actor, "nearby").forEach {
                it.showTitle(net.kyori.adventure.title.Title.title(
                    net.kyori.adventure.text.Component.text(command.string("title")),
                    net.kyori.adventure.text.Component.text(command.string("subtitle")),
                    net.kyori.adventure.title.Title.Times.times(
                        Duration.ofMillis(command.int("fadeIn", 10) * 50L),
                        Duration.ofMillis(command.int("stay", 60) * 50L),
                        Duration.ofMillis(command.int("fadeOut", 10) * 50L)
                    )
                ))
            }
            1L
        }
        CommandType.ACTIONBAR -> {
            targets(origin, actor, "nearby").forEach { it.sendActionBar(net.kyori.adventure.text.Component.text(command.string("text"))) }
            1L
        }
        CommandType.EFFECT -> {
            val type = registryValue(Registry.POTION_EFFECT_TYPE, command.string("effect", "minecraft:speed"))
            if (type != null) {
                targets(origin, actor, "nearby").forEach { it.addPotionEffect(PotionEffect(type, command.int("duration", 100), command.int("amplifier", 0))) }
            }
            1L
        }
    }

    private fun targets(origin: Location, actor: Player?, mode: String): List<Player> = when (mode) {
        "self" -> listOfNotNull(actor)
        else -> origin.world.players.filter { it.location.distanceSquared(origin) <= 32.0 * 32.0 }
    }

    private fun <T : org.bukkit.Keyed> registryValue(registry: Registry<T>, raw: String): T? {
        val normalized = raw.lowercase().let { if (":" in it) it else "minecraft:$it" }
        val key = NamespacedKey.fromString(normalized) ?: return null
        return registry.get(key)
    }
}
