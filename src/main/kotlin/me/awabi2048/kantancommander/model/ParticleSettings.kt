package me.awabi2048.kantancommander.model

import me.awabi2048.kantancommander.item.ItemStackCodec
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Vibration
import org.bukkit.block.data.BlockData
import org.bukkit.inventory.ItemStack
import java.util.Locale

/**
 * PARTICLEノードの保存キーと、Particleの追加データを一元的に扱います。
 *
 * BukkitのParticle APIは種類によってdata引数の型が異なります。GUI・実行前検証・
 * 通常実行・データパック出力がそれぞれ独自の文字列解釈を持つと、同じ設定でも
 * 実行経路ごとに表示結果が変わります。そのため保存形式は人間が編集できる1本の
 * 詳細文字列に限定し、このクラスで型付き値へ変換する境界を固定します。
 */
object ParticleSettings {
    const val PARAM_PARTICLE = "particle"
    const val PARAM_DELTA_X = "particleDeltaX"
    const val PARAM_DELTA_Y = "particleDeltaY"
    const val PARAM_DELTA_Z = "particleDeltaZ"
    const val PARAM_SPEED = "particleSpeed"
    const val PARAM_COUNT = "particleCount"
    const val PARAM_DATA = "particleData"

    fun particle(node: CommandNode): Particle? =
        runCatching { Particle.valueOf(node.string(PARAM_PARTICLE, Particle.FLAME.name)) }.getOrNull()

    fun requiresData(particle: Particle): Boolean = particle.dataType != Void::class.java

    /**
     * 詳細欄の形式をParticleごとのBukkitデータへ変換します。
     *
     * 色は通常 `#RRGGBB` または `R G B`、ENTITY_EFFECT/FLASHは
     * `#AARRGGBB` または `R G B A` も受け付けます。DUST系は色の後ろにサイズ、
     * DUST_COLOR_TRANSITIONは「開始色 終了色 サイズ」を受け付けます。
     * VIBRATION/TRAILの座標は、表示位置と同じワールド内の絶対座標です。
     */
    fun parseData(particle: Particle, raw: String): Result<ParticleDataSpec> = runCatching {
        val tokens = tokenize(raw)
        when (particle.dataType) {
            Void::class.java -> {
                require(tokens.isEmpty()) { "このパーティクルは詳細データを持ちません" }
                ParticleDataSpec.None
            }
            Color::class.java -> {
                val parsed = parseArgbColor(tokens, 0)
                require(parsed.third == tokens.size) { "色の形式が不正です" }
                ParticleDataSpec.ColorValue(parsed.first, parsed.second)
            }
            Particle.DustOptions::class.java -> {
                val (color, next) = parseColor(tokens, 0)
                require(tokens.size == next + 1) { "色とサイズを指定してください" }
                ParticleDataSpec.Dust(color, parseFiniteFloat(tokens[next], "サイズ"))
            }
            Particle.DustTransition::class.java -> {
                val (from, afterFrom) = parseColor(tokens, 0)
                val (to, afterTo) = parseColor(tokens, afterFrom)
                require(tokens.size == afterTo + 1) { "開始色、終了色、サイズを指定してください" }
                ParticleDataSpec.DustTransition(from, to, parseFiniteFloat(tokens[afterTo], "サイズ"))
            }
            Particle.Spell::class.java -> {
                val (color, next) = parseColor(tokens, 0)
                require(tokens.size == next + 1) { "色とパワーを指定してください" }
                ParticleDataSpec.Spell(color, parseFiniteFloat(tokens[next], "パワー"))
            }
            ItemStack::class.java -> {
                require(tokens.size == 1) { "アイテムIDまたはItemStackデータを1つ指定してください" }
                val value = tokens.single()
                val stack = ItemStackCodec.decode(value) ?: run {
                    val material = Material.matchMaterial(value)
                    require(material != null && !material.isAir) { "アイテムIDが不正です" }
                    ItemStack(material)
                }
                ParticleDataSpec.Item(value, stack)
            }
            BlockData::class.java -> {
                require(tokens.size == 1) { "ブロック状態を1つ指定してください" }
                val value = tokens.single()
                val materialName = value.substringBefore('[').removePrefix("minecraft:")
                val material = Material.matchMaterial(materialName)
                require(material != null && material.isBlock) { "ブロック状態が不正です" }
                ParticleDataSpec.Block(value)
            }
            Float::class.javaObjectType -> {
                require(tokens.size == 1) { "数値を1つ指定してください" }
                ParticleDataSpec.FloatValue(parseFiniteFloat(tokens.single(), "値"))
            }
            Int::class.javaObjectType -> {
                require(tokens.size == 1) { "整数を1つ指定してください" }
                val value = tokens.single().toIntOrNull()
                require(value != null && value >= 0) { "0以上の整数を指定してください" }
                ParticleDataSpec.IntegerValue(value)
            }
            Vibration::class.java -> {
                require(tokens.size == 4) { "終点X、終点Y、終点Z、到達tickを指定してください" }
                ParticleDataSpec.VibrationValue(
                    parseFiniteDouble(tokens[0], "終点X"),
                    parseFiniteDouble(tokens[1], "終点Y"),
                    parseFiniteDouble(tokens[2], "終点Z"),
                    tokens[3].toIntOrNull()?.also { require(it >= 0) { "到達tickは0以上です" } }
                        ?: error("到達tickが不正です"),
                )
            }
            Particle.Trail::class.java -> {
                require(tokens.size == 5) { "終点X、終点Y、終点Z、色、表示tickを指定してください" }
                val (color, alpha, afterColor) = parseArgbColor(tokens, 3)
                require(afterColor == tokens.size - 1) { "色の形式が不正です" }
                ParticleDataSpec.TrailValue(
                    parseFiniteDouble(tokens[0], "終点X"),
                    parseFiniteDouble(tokens[1], "終点Y"),
                    parseFiniteDouble(tokens[2], "終点Z"),
                    color,
                    alpha,
                    tokens[4].toIntOrNull()?.also { require(it >= 0) { "表示tickは0以上です" } }
                        ?: error("表示tickが不正です"),
                )
            }
            else -> error("未対応のParticleデータ型です: ${particle.dataType.name}")
        }
    }

    private fun tokenize(raw: String): List<String> =
        raw.trim().split(Regex("[,\\s]+")).filter(String::isNotBlank)

    private fun parseColor(tokens: List<String>, start: Int): Pair<Color, Int> {
        require(start < tokens.size) { "色が未設定です" }
        val single = tokens[start]
        if (single.startsWith("#") || single.startsWith("0x", ignoreCase = true)) {
            val hex = single.removePrefix("#").removePrefix("0x").removePrefix("0X")
            require(hex.matches(Regex("[0-9a-fA-F]{6}"))) { "色は#RRGGBB形式です" }
            return Color.fromRGB(hex.toInt(16)) to start + 1
        }
        require(start + 2 < tokens.size) { "色はR G B形式です" }
        val rgb = (start..start + 2).map { index ->
            tokens[index].toIntOrNull()?.also { require(it in 0..255) { "色の各値は0〜255です" } }
                ?: error("色の各値は整数です")
        }
        return Color.fromRGB(rgb[0], rgb[1], rgb[2]) to start + 3
    }

    /** ENTITY_EFFECT/FLASH用の色です。alphaは#AARRGGBBまたは末尾の0..255で受け付けます。 */
    private fun parseArgbColor(tokens: List<String>, start: Int): Triple<Color, Float, Int> {
        require(start < tokens.size) { "色が未設定です" }
        val single = tokens[start]
        if (single.startsWith("#") || single.startsWith("0x", ignoreCase = true)) {
            val hex = single.removePrefix("#").removePrefix("0x").removePrefix("0X")
            require(hex.matches(Regex("(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})"))) {
                "色は#RRGGBBまたは#AARRGGBB形式です"
            }
            val hasAlpha = hex.length == 8
            val alpha = if (hasAlpha) hex.substring(0, 2).toInt(16) else 255
            val rgb = if (hasAlpha) hex.substring(2) else hex
            return Triple(Color.fromRGB(rgb.toInt(16)), alpha / 255.0f, start + 1)
        }
        require(start + 2 < tokens.size) { "色はR G B形式です" }
        val rgb = (start..start + 2).map { index ->
            tokens[index].toIntOrNull()?.also { require(it in 0..255) { "色の各値は0〜255です" } }
                ?: error("色の各値は整数です")
        }
        val hasAlpha = start + 3 < tokens.size
        val alpha = if (hasAlpha) {
            tokens[start + 3].toIntOrNull()?.also { require(it in 0..255) { "アルファ値は0〜255です" } }
                ?: error("アルファ値は整数です")
        } else {
            255
        }
        return Triple(Color.fromRGB(rgb[0], rgb[1], rgb[2]), alpha / 255.0f, start + if (hasAlpha) 4 else 3)
    }

    private fun parseFiniteDouble(raw: String, label: String): Double =
        raw.toDoubleOrNull()?.takeIf(Double::isFinite) ?: error("${label}は有限値です")

    private fun parseFiniteFloat(raw: String, label: String): Float =
        raw.toFloatOrNull()?.takeIf(Float::isFinite) ?: error("${label}は有限値です")

    /** 実行時・送信時に使う、型付きParticleデータです。 */
    sealed interface ParticleDataSpec {
        fun toBukkitData(origin: Location): Any?

        /**
         * 現行Java版の/particleへ渡すSNBTオプションを生成します。
         *
         * 1.20.5以降はParticleの追加データが旧来の空白区切り引数ではなく、
         * worldgenと同じSNBT表現になりました。Particleごとに同じBukkit型でも
         * キー（color、power、rollなど）が異なるため、種類を明示的に受け取ります。
         */
        fun vanillaArgument(particle: Particle): String

        data object None : ParticleDataSpec {
            override fun toBukkitData(origin: Location): Any? = null
            override fun vanillaArgument(particle: Particle): String = ""
        }

        data class ColorValue(val color: Color, val alpha: Float = 1.0f) : ParticleDataSpec {
            override fun toBukkitData(origin: Location): Any = color
            override fun vanillaArgument(particle: Particle): String {
                // BukkitのColorは不透明色として保存されるため、alphaはデータパック
                // 出力へだけ保持します。TINTED_LEAVESもENTITY_EFFECT/FLASHと同じ
                // Color型ですが、Paper 26.1系のParticle列挙に合わせてここで許可します。
                require(particle == Particle.ENTITY_EFFECT || particle == Particle.FLASH || particle == Particle.TINTED_LEAVES) {
                    "色データに対応していないParticleです: ${particle.name}"
                }
                return "{color:${argbColorArgument(color, alpha)}}"
            }
        }

        data class Dust(val color: Color, val size: Float) : ParticleDataSpec {
            override fun toBukkitData(origin: Location): Any = Particle.DustOptions(color, size)
            override fun vanillaArgument(particle: Particle): String {
                require(particle == Particle.DUST) { "DUSTデータに対応していないParticleです: ${particle.name}" }
                return "{color:${rgbColorArgument(color)},scale:${number(size)}}"
            }
        }

        data class DustTransition(val from: Color, val to: Color, val size: Float) : ParticleDataSpec {
            override fun toBukkitData(origin: Location): Any = Particle.DustTransition(from, to, size)
            override fun vanillaArgument(particle: Particle): String {
                require(particle == Particle.DUST_COLOR_TRANSITION) {
                    "DUST_COLOR_TRANSITIONデータに対応していないParticleです: ${particle.name}"
                }
                return "{from_color:${rgbColorArgument(from)},scale:${number(size)},to_color:${rgbColorArgument(to)}}"
            }
        }

        data class Spell(val color: Color, val power: Float) : ParticleDataSpec {
            override fun toBukkitData(origin: Location): Any = Particle.Spell(color, power)
            override fun vanillaArgument(particle: Particle): String {
                require(particle == Particle.EFFECT || particle == Particle.INSTANT_EFFECT) {
                    "Spellデータに対応していないParticleです: ${particle.name}"
                }
                return "{color:${rgbColorArgument(color)},power:${number(power)}}"
            }
        }

        data class Item(val raw: String, val stack: ItemStack) : ParticleDataSpec {
            override fun toBukkitData(origin: Location): Any = stack.clone()
            override fun vanillaArgument(particle: Particle): String {
                require(particle == Particle.ITEM) { "ITEMデータに対応していないParticleです: ${particle.name}" }
                return "{item:${snbtString(raw)}}"
            }
        }

        data class Block(val raw: String) : ParticleDataSpec {
            override fun toBukkitData(origin: Location): Any? =
                runCatching { Bukkit.createBlockData(raw) }.getOrNull()

            override fun vanillaArgument(particle: Particle): String {
                require(particle in BLOCK_DATA_PARTICLES) {
                    "ブロックデータに対応していないParticleです: ${particle.name}"
                }
                return blockStateArgument(raw)
            }
        }

        data class FloatValue(val value: Float) : ParticleDataSpec {
            override fun toBukkitData(origin: Location): Any = value
            override fun vanillaArgument(particle: Particle): String = when (particle) {
                Particle.DRAGON_BREATH -> "{power:${number(value)}}"
                Particle.SCULK_CHARGE -> "{roll:${number(value)}}"
                else -> error("数値データに対応していないParticleです: ${particle.name}")
            }
        }

        data class IntegerValue(val value: Int) : ParticleDataSpec {
            override fun toBukkitData(origin: Location): Any = value
            override fun vanillaArgument(particle: Particle): String {
                require(particle == Particle.SHRIEK) { "整数データに対応していないParticleです: ${particle.name}" }
                return "{delay:$value}"
            }
        }

        data class VibrationValue(
            val x: Double,
            val y: Double,
            val z: Double,
            val arrivalTicks: Int,
        ) : ParticleDataSpec {
            override fun toBukkitData(origin: Location): Any {
                val target = Location(origin.world, x, y, z)
                return Vibration(
                    Vibration.Destination.BlockDestination(target),
                    arrivalTicks,
                )
            }

            override fun vanillaArgument(particle: Particle): String {
                require(particle == Particle.VIBRATION) { "振動データに対応していないParticleです: ${particle.name}" }
                return "{destination:{type:block,pos:[${number(x)},${number(y)},${number(z)}]},arrival_in_ticks:$arrivalTicks}"
            }
        }

        data class TrailValue(
            val x: Double,
            val y: Double,
            val z: Double,
            val color: Color,
            val alpha: Float = 1.0f,
            val durationTicks: Int,
        ) : ParticleDataSpec {
            override fun toBukkitData(origin: Location): Any = Particle.Trail(
                Location(origin.world, x, y, z),
                color,
                durationTicks,
            )

            override fun vanillaArgument(particle: Particle): String {
                require(particle == Particle.TRAIL) { "Trailデータに対応していないParticleです: ${particle.name}" }
                // trail.colorはARGBです。BukkitのTrailはColorしか受け取らないため、
                // alphaは通常実行では失われますが、データパック出力では保持します。
                return "{target:[${number(x)},${number(y)},${number(z)}],color:${argbColorArgument(color, alpha)},duration:$durationTicks}"
            }
        }
    }

    private val BLOCK_DATA_PARTICLES = setOf(
        Particle.BLOCK,
        Particle.BLOCK_MARKER,
        Particle.BLOCK_CRUMBLE,
        Particle.DUST_PILLAR,
        Particle.FALLING_DUST,
    )

    private fun rgbColorArgument(color: Color): String = listOf(
        color.getRed() / 255.0,
        color.getGreen() / 255.0,
        color.getBlue() / 255.0,
    ).joinToString(",", prefix = "[", postfix = "]", transform = ::number)

    private fun argbColorArgument(color: Color, alpha: Float): String = listOf(
        color.getRed() / 255.0,
        color.getGreen() / 255.0,
        color.getBlue() / 255.0,
        alpha,
    ).joinToString(",", prefix = "[", postfix = "]", transform = ::number)

    /** block_stateの短縮表記を、現行/particleが受け付けるSNBTへ変換します。 */
    private fun blockStateArgument(raw: String): String {
        val value = raw.trim()
        val name = value.substringBefore('[').let { if (it.contains(':')) it else "minecraft:$it" }
        require(name.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+"))) { "ブロックIDが不正です" }
        val properties = value.substringAfter('[', missingDelimiterValue = "")
        if (properties.isBlank()) return "{block_state:${snbtString(name)}}"
        require(properties.endsWith(']')) { "ブロック状態の括弧が不正です" }
        val entries = properties.dropLast(1).split(',').filter(String::isNotBlank)
        require(entries.isNotEmpty()) { "ブロック状態が空です" }
        val rendered = entries.joinToString(",") { entry ->
            val key = entry.substringBefore('=', missingDelimiterValue = "").trim()
            val propertyValue = entry.substringAfter('=', missingDelimiterValue = "").trim()
            require(key.matches(Regex("[a-z0-9_.-]+")) && propertyValue.matches(Regex("[a-z0-9_.-]+"))) {
                "ブロック状態のプロパティが不正です"
            }
            "${snbtString(key)}:${snbtString(propertyValue)}"
        }
        return "{block_state:{Name:${snbtString(name)},Properties:{$rendered}}}"
    }

    private fun snbtString(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun number(value: Number): String =
        String.format(Locale.ROOT, "%.6f", value.toDouble()).trimEnd('0').trimEnd('.')
}
