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
 * 検証の正本は `CommandDialogSpecs` 側に残し、ここでは範囲・刻み・振り分けと
 * runtime-dependent表示方針だけを固定します。
 */
class SettingSpecTest {
    @Test
    fun `volume pitch intensity have slider specs within dialog validation`() {
        val volume = SettingSpec.sliderSpecFor(CommandType.PLAY_SOUND, "volume")
        assertNotNull(volume)
        assertEquals(0.0, volume!!.min)
        assertEquals(34.0, volume.max)

        val pitch = SettingSpec.sliderSpecFor(CommandType.PLAY_SOUND, "pitch")
        assertNotNull(pitch)
        assertEquals(0.5, pitch!!.min)
        assertEquals(2.0, pitch.max)

        val intensity = SettingSpec.sliderSpecFor(CommandType.CAMERA_SHAKE, "intensity")
        assertNotNull(intensity)
        assertEquals(0.1, intensity!!.min)
        assertEquals(4.0, intensity.max)
    }

    @Test
    fun `slider clamps rounds and formats without float drift`() {
        val pitch = SettingSpec.sliderSpecFor(CommandType.PLAY_SOUND, "pitch")!!
        // 上限を超えた値は丸めず上限へ寄せます。
        assertEquals("2", SliderValuePolicy.format(pitch, 99.0))
        // 下限を下回った値は下限へ寄せます。
        assertEquals("0.5", SliderValuePolicy.format(pitch, -99.0))
        // 刻みへ丸めます（0.5 + 0.1 * n）。
        assertEquals("1", SliderValuePolicy.format(pitch, 1.04))
        // 整数項目は小数表示にしません。
        val level = SettingSpec.sliderSpecFor(CommandType.APPLY_EFFECT, "level")!!
        assertTrue(level.integer)
        assertEquals("3", SliderValuePolicy.format(level, 2.6))
        // 1刻みの増減が保存文字列になります。
        assertEquals("2", SliderValuePolicy.stepBy(pitch, "1.9", 1))
        assertEquals("0.5", SliderValuePolicy.stepBy(pitch, "0.5", -1))
    }

    @Test
    fun `slider rejects non finite and falls back to default`() {
        val volume = SettingSpec.sliderSpecFor(CommandType.PLAY_SOUND, "volume")!!
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
        assertEquals(
            GesturePresentation.SLIDER,
            SettingSpec.presentationFor(sound, "volume", CommandSettingsModel.descriptor(sound, "volume")),
        )
        // テンプレート参照を含む数値はSliderへ載せません。
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
        assertNull(SettingSpec.sliderSpecFor(CommandType.WAIT, "unknownField"))
        assertNull(SettingSpec.sliderSpecFor(CommandType.MERGE, "seconds"))
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

    @Test
    fun `geometry sliders follow model ranges`() {
        val radius = GeometrySliderPolicy.sliderSpecFor("geometryRadius")
        assertNotNull(radius)
        assertEquals(32.0, radius!!.max)
        assertNull(GeometrySliderPolicy.sliderSpecFor("unknownGeometryKey"))
    }
}
