package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.TargetKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * issue #18 の値仕様分離の回帰テストです。
 *
 * 範囲・刻み・既定値の正本は `CommandDialogSpecs` 側にあり、Slider仕様は
 * そこからの導出です。ここでは導出の正しさと共有契約だけを固定します。
 */
class SettingSpecTest {
    private fun node(type: CommandType) = type.newNode()

    @Test
    fun `numeric ranges stay on dialog specs while slider stays disabled`() {
        // Slider操作は経路から外しているため、範囲は検証の正本として残します。
        val volume = requireNotNull(CommandDialogSpecs.field(node(CommandType.PLAY_SOUND), "volume")).numericRange
        assertNotNull(volume)
        assertEquals(0.0, volume!!.min)
        assertEquals(34.0, volume.max)

        val pitch = requireNotNull(CommandDialogSpecs.field(node(CommandType.PLAY_SOUND), "pitch")).numericRange
        assertNotNull(pitch)
        assertEquals(0.5, pitch!!.min)
        assertEquals(2.0, pitch.max)

        val intensity = requireNotNull(CommandDialogSpecs.field(node(CommandType.CAMERA_SHAKE), "intensity")).numericRange
        assertNotNull(intensity)
        assertEquals(0.1, intensity!!.min)
        assertEquals(4.0, intensity.max)

        // 操作経路のSlider導出は無効化されています。
        assertNull(SettingSpec.sliderSpecFor(node(CommandType.PLAY_SOUND), "volume"))
        assertNull(SettingSpec.sliderSpecFor(node(CommandType.PLAY_SOUND), "pitch"))
        assertNull(SettingSpec.sliderSpecFor(node(CommandType.CAMERA_SHAKE), "intensity"))
    }

    @Test
    fun `bounded numerics fall back to dialog input`() {
        // Slider無効化中は、有界数値を含む全項目がテキスト入力になります。
        // 検証仕様自体は残り、入力値は検証を通過します。
        val cases = listOf(
            CommandType.PLAY_SOUND to "volume",
            CommandType.PLAY_SOUND to "pitch",
            CommandType.CAMERA_SHAKE to "intensity",
            CommandType.CAMERA_SHAKE to "seconds",
            CommandType.APPLY_EFFECT to "level",
            CommandType.APPLY_EFFECT to "seconds",
            CommandType.WAIT to "seconds",
            CommandType.DISPLAY_TEXT to "staySeconds",
            CommandType.DISPLAY_TEXT to "fadeInSeconds",
            CommandType.DISPLAY_TEXT to "fadeOutSeconds",
            CommandType.FIREWORK_LAUNCH to "burstHeight",
            CommandType.PARTICLE to "particleDensity",
        )
        cases.forEach { (type, fieldKey) ->
            val host = node(type)
            assertNull(SettingSpec.sliderSpecFor(host, fieldKey), "$type.$fieldKey")
            val dialog = CommandDialogSpecs.field(host, fieldKey)
            assertNotNull(dialog, "$type.$fieldKey")
            assertEquals(
                GesturePresentation.INPUT_DIALOG,
                SettingSpec.presentationFor(host, fieldKey, CommandSettingsModel.descriptor(host, fieldKey)),
                "$type.$fieldKey",
            )
        }
    }

    @Test
    fun `unbounded quantities stay on text input`() {
        // 上限の定まらない数量系はSliderに載せず、テキスト入力のまま検証します。
        val cases = listOf(
            CommandType.GIVE_ITEM to "count",
            CommandType.FOR_START to "count",
            CommandType.PARTICLE to me.awabi2048.kantancommander.model.ParticleSettings.PARAM_COUNT,
        )
        cases.forEach { (type, fieldKey) ->
            val host = node(type)
            assertNull(SettingSpec.sliderSpecFor(host, fieldKey), "$type.$fieldKey")
            val dialog = CommandDialogSpecs.field(host, fieldKey)
            assertNotNull(dialog, "$type.$fieldKey")
            assertNull(dialog!!.validateInput("3"), "$type.$fieldKey")
        }
    }

    @Test
    fun `slider policy clamps rounds and formats without float drift`() {
        // 数値操作の純粋仕様はSlider復活時の資産として固定します。
        val pitch = SliderSpec(min = 0.5, max = 2.0, step = 0.1, integer = false, defaultValue = "1.0")
        // 上限を超えた値は丸めず上限へ寄せます。
        assertEquals("2", SliderValuePolicy.format(pitch, 99.0))
        // 下限を下回った値は下限へ寄せます。
        assertEquals("0.5", SliderValuePolicy.format(pitch, -99.0))
        // 刻みへ丸めます（0.5 + 0.1 * n）。
        assertEquals("1", SliderValuePolicy.format(pitch, 1.04))
        // 整数項目は小数表示にしません。
        val level = SliderSpec(min = 1.0, max = 255.0, step = 1.0, integer = true, defaultValue = "1")
        assertTrue(level.integer)
        assertEquals("3", SliderValuePolicy.format(level, 2.6))
        // 1刻みの増減が保存文字列になります。
        assertEquals("2", SliderValuePolicy.stepBy(pitch, "1.9", 1))
        assertEquals("0.5", SliderValuePolicy.stepBy(pitch, "0.5", -1))
    }

    @Test
    fun `slider policy rejects non finite and falls back to default`() {
        val volume = SliderSpec(min = 0.0, max = 34.0, step = 1.0, integer = false, defaultValue = "1.0")
        assertEquals("1", SliderValuePolicy.format(volume, Double.NaN))
        assertEquals("1", SliderValuePolicy.parse(volume, null).toString().take(1))
        assertEquals("1", SliderValuePolicy.format(volume, SliderValuePolicy.parse(volume, "not-a-number")))
    }

    @Test
    fun `template references stay on dialog`() {
        assertTrue(SliderValuePolicy.isTemplateReference("\${score}"))
        assertTrue(SliderValuePolicy.isTemplateReference("%{base}%"))
        assertFalse(SliderValuePolicy.isTemplateReference("1.5"))
        assertFalse(SliderValuePolicy.isTemplateReference(null))
    }

    @Test
    fun `checkbox and dialog classification follow the table`() {
        assertTrue(SettingSpec.isCheckbox(CommandType.ENTITY_ACTION, "overwrite"))
        assertTrue(SettingSpec.isCheckbox(CommandType.CONDITION, "inverted"))
        assertFalse(SettingSpec.isCheckbox(CommandType.PLAY_SOUND, "volume"))

        val sound = CommandType.PLAY_SOUND.newNode()
        // Slider無効化中は数値項目もテキスト入力になります。
        assertEquals(
            GesturePresentation.INPUT_DIALOG,
            SettingSpec.presentationFor(sound, "volume", CommandSettingsModel.descriptor(sound, "volume")),
        )
        // テンプレート参照を含む数値もDialogで編集します。
        sound.params["volume"] = "\${score}"
        assertEquals(
            GesturePresentation.INPUT_DIALOG,
            SettingSpec.presentationFor(sound, "volume", CommandSettingsModel.descriptor(sound, "volume")),
        )

        val overwrite = CommandType.ENTITY_ACTION.newNode()
        assertEquals(
            GesturePresentation.CHECKBOX,
            SettingSpec.presentationFor(overwrite, "overwrite", CommandSettingsModel.descriptor(overwrite, "overwrite")),
        )

        val text = CommandType.DISPLAY_TEXT.newNode()
        assertEquals(
            GesturePresentation.INPUT_DIALOG,
            SettingSpec.presentationFor(text, "text", CommandSettingsModel.descriptor(text, "text")),
        )
    }

    @Test
    fun `unknown fields have no slider`() {
        assertNull(SettingSpec.sliderSpecFor(node(CommandType.WAIT), "unknownField"))
        assertNull(SettingSpec.sliderSpecFor(node(CommandType.MERGE), "seconds"))
    }

    @Test
    fun `bar segments resolve to stepped preview values`() {
        // 区画解決の純粋仕様はSlider復活時の資産として固定します。
        val volume = SliderSpec(min = 0.0, max = 34.0, step = 1.0, integer = false, defaultValue = "1.0")
        // 12区画の両端は範囲内へ収まり、刻みへ丸められます。
        assertEquals("1", SliderValuePolicy.valueForSegment(volume, 0))
        assertEquals("33", SliderValuePolicy.valueForSegment(volume, 11))
        // 範囲外区画は端へ寄せます。
        assertEquals(SliderValuePolicy.valueForSegment(volume, 0), SliderValuePolicy.valueForSegment(volume, -5))
        assertEquals(SliderValuePolicy.valueForSegment(volume, 11), SliderValuePolicy.valueForSegment(volume, 99))
        // 整数欄は整数表示になります。
        val level = SliderSpec(min = 1.0, max = 255.0, step = 1.0, integer = true, defaultValue = "1")
        assertEquals("12", SliderValuePolicy.valueForSegment(level, 0))
        // 区画値は検証を通過します。
        val dialog = CommandDialogSpecs.field(node(CommandType.PLAY_SOUND), "volume")!!
        (0 until SettingSpec.SLIDER_SEGMENTS).forEach { index ->
            assertNull(dialog.validateInput(SliderValuePolicy.valueForSegment(volume, index)), "segment $index")
        }
    }

    @Test
    fun `runtime dependent targets stay visible`() {
        assertTrue(RuntimeDependentPolicy.isRuntimeDependentTarget(TargetKind.TEMPORARY))
        assertTrue(RuntimeDependentPolicy.isRuntimeDependentTarget(TargetKind.NEAREST_ENTITY))
        assertTrue(RuntimeDependentPolicy.isRuntimeDependentTarget(TargetKind.NEARBY_ENTITIES))
        assertFalse(RuntimeDependentPolicy.isRuntimeDependentTarget(TargetKind.NEAREST_PLAYER))
        assertFalse(RuntimeDependentPolicy.isRuntimeDependentTarget(null))

        val node = CommandType.SUMMON_ENTITY.newNode()
        // runtime-dependentな対象では設定項目を隠しません。
        assertFalse(RuntimeDependentPolicy.canHide(node, TargetKind.TEMPORARY))
        assertTrue(RuntimeDependentPolicy.canHide(node, TargetKind.NEAREST_PLAYER))
    }
}
