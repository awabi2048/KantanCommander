package me.awabi2048.kantancommander.model

import net.kyori.adventure.text.Component
import org.bukkit.NamespacedKey

/**
 * 効果の表示名をバニラ翻訳で解決する境界です。
 *
 * 独自の翻訳表は持たず、`effect.<namespace>.<path>` 形式のバニラ翻訳キーへの
 * `Component.translatable` を返して各クライアントの言語で描画させます。
 * 未解決IDはID原文を返します。GUI表示専用であり、検証・実行・出力の
 * ID解決には使いません。
 */
object EffectDisplay {
    /**
     * 種別IDに対応する「表示名（ID）」形式の表示用Componentを返します。
     *
     * 一覧のホバーで名前とIDを1行にまとめて示します。IDが空欄のときは
     * 空の表示を返し、他項目の未設定表示と揃えます。
     */
    fun labeledFor(rawId: String): Component {
        val raw = rawId.trim()
        if (raw.isEmpty()) return Component.text(raw)
        val key = NamespacedKey.fromString(raw) ?: return Component.text(raw)
        return Component.translatable("effect.${key.namespace}.${key.key}")
            .append(Component.text("（$raw）"))
    }
}
