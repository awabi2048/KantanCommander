package me.awabi2048.kantancommander.data.model

import org.bukkit.Material

enum class CommandType(
    val id: String,
    val displayNameKey: String,
    val icon: Material,
    val paramDefinitions: List<ParamDef>
) {
    SOUND("sound", "command_type.sound", Material.NOTE_BLOCK, listOf(
        ParamDef.SOUND, ParamDef.VOLUME, ParamDef.PITCH, ParamDef.CATEGORY
    )),
    MESSAGE("message", "command_type.message", Material.PAPER, listOf(
        ParamDef.TEXT, ParamDef.MESSAGE_TARGET
    )),
    PARTICLE("particle", "command_type.particle", Material.FIREWORK_STAR, listOf(
        ParamDef.PARTICLE, ParamDef.COUNT, ParamDef.SPEED, ParamDef.OFFSET_X, ParamDef.OFFSET_Y, ParamDef.OFFSET_Z
    )),
    WAIT("wait", "command_type.wait", Material.CLOCK, listOf(
        ParamDef.TICKS
    )),
    TITLE("title", "command_type.title", Material.NAME_TAG, listOf(
        ParamDef.TITLE_TEXT, ParamDef.SUBTITLE_TEXT, ParamDef.FADE_IN, ParamDef.STAY, ParamDef.FADE_OUT
    )),
    ACTIONBAR("actionbar", "command_type.actionbar", Material.WRITABLE_BOOK, listOf(
        ParamDef.TEXT
    )),
    EFFECT("effect", "command_type.effect", Material.POTION, listOf(
        ParamDef.EFFECT_TYPE, ParamDef.DURATION, ParamDef.AMPLIFIER
    ));

    sealed interface ParamDef {
        val key: String
        val displayNameKey: String

        data object SOUND : ParamDef {
            override val key = "sound"
            override val displayNameKey = "param.sound"
        }
        data object VOLUME : ParamDef {
            override val key = "volume"
            override val displayNameKey = "param.volume"
        }
        data object PITCH : ParamDef {
            override val key = "pitch"
            override val displayNameKey = "param.pitch"
        }
        data object CATEGORY : ParamDef {
            override val key = "category"
            override val displayNameKey = "param.category"
        }
        data object TEXT : ParamDef {
            override val key = "text"
            override val displayNameKey = "param.text"
        }
        data object MESSAGE_TARGET : ParamDef {
            override val key = "target"
            override val displayNameKey = "param.message_target"
        }
        data object PARTICLE : ParamDef {
            override val key = "particle"
            override val displayNameKey = "param.particle"
        }
        data object COUNT : ParamDef {
            override val key = "count"
            override val displayNameKey = "param.count"
        }
        data object SPEED : ParamDef {
            override val key = "speed"
            override val displayNameKey = "param.speed"
        }
        data object OFFSET_X : ParamDef {
            override val key = "offsetX"
            override val displayNameKey = "param.offset_x"
        }
        data object OFFSET_Y : ParamDef {
            override val key = "offsetY"
            override val displayNameKey = "param.offset_y"
        }
        data object OFFSET_Z : ParamDef {
            override val key = "offsetZ"
            override val displayNameKey = "param.offset_z"
        }
        data object TICKS : ParamDef {
            override val key = "ticks"
            override val displayNameKey = "param.ticks"
        }
        data object TITLE_TEXT : ParamDef {
            override val key = "title"
            override val displayNameKey = "param.title"
        }
        data object SUBTITLE_TEXT : ParamDef {
            override val key = "subtitle"
            override val displayNameKey = "param.subtitle"
        }
        data object FADE_IN : ParamDef {
            override val key = "fadeIn"
            override val displayNameKey = "param.fade_in"
        }
        data object STAY : ParamDef {
            override val key = "stay"
            override val displayNameKey = "param.stay"
        }
        data object FADE_OUT : ParamDef {
            override val key = "fadeOut"
            override val displayNameKey = "param.fade_out"
        }
        data object EFFECT_TYPE : ParamDef {
            override val key = "effect"
            override val displayNameKey = "param.effect_type"
        }
        data object DURATION : ParamDef {
            override val key = "duration"
            override val displayNameKey = "param.duration"
        }
        data object AMPLIFIER : ParamDef {
            override val key = "amplifier"
            override val displayNameKey = "param.amplifier"
        }
    }
}
