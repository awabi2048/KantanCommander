package me.awabi2048.kantancommander.command

/**
 * `/kankoma` の各入口で使う権限ノードを一箇所へ集約します。
 *
 * Bukkit のコマンド定義は親コマンドに一つの権限しか設定できないため、
 * `plugin.yml` の親コマンド権限ではなく、実行時にサブコマンド単位で判定します。
 * これにより `/kankoma history` と `/kankoma library` を独立して委譲できます。
 */
internal object KantanCommandPermissions {
    const val ADMIN = "kankoma.admin"
    const val ROOT = "kankoma.command.kankoma"
    const val HISTORY = "kankoma.command.history"
    const val LIBRARY = "kankoma.command.library"
    const val HELP = "kankoma.command.help"
    const val PLACED = "kankoma.command.placed"
    const val RELOAD = "kankoma.command.reload"

    fun forSubcommand(subcommand: String?): String? = when (subcommand?.lowercase()) {
        null -> ROOT
        "history" -> HISTORY
        "library" -> LIBRARY
        "help" -> HELP
        "placed" -> PLACED
        "reload" -> RELOAD
        else -> null
    }
}
