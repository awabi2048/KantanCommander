package me.awabi2048.kantancommander.data.model

data class ScriptCommand(
    val type: CommandType,
    val params: MutableMap<String, String> = mutableMapOf()
) {
    fun getString(key: String, default: String = ""): String =
        params[key] ?: default

    fun getInt(key: String, default: Int = 0): Int =
        params[key]?.toIntOrNull() ?: default

    fun getFloat(key: String, default: Float = 0f): Float =
        params[key]?.toFloatOrNull() ?: default

    fun getDouble(key: String, default: Double = 0.0): Double =
        params[key]?.toDoubleOrNull() ?: default

    fun <T : Enum<T>> getEnum(key: String, enumClass: Class<T>, default: T): T {
        val raw = params[key] ?: return default
        return try {
            java.lang.Enum.valueOf(enumClass, raw.uppercase())
        } catch (_: IllegalArgumentException) {
            default
        }
    }

    fun paramSummary(): String = when (type) {
        CommandType.SOUND -> getString("sound", "?")
        CommandType.MESSAGE -> getString("text", "?").take(20)
        CommandType.PARTICLE -> getString("particle", "?")
        CommandType.WAIT -> "${getInt("ticks", 0)}t"
        CommandType.TITLE -> getString("title", "?").take(20)
        CommandType.ACTIONBAR -> getString("text", "?").take(20)
        CommandType.EFFECT -> getString("effect", "?").take(20)
    }
}
