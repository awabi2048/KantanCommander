package me.awabi2048.kantancommander.model

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.entity.EntityType

/**
 * エンティティ種別の表示名をバニラ翻訳で解決する境界です。
 *
 * 独自の翻訳表は持たず、`entity.<namespace>.<path>` 形式のバニラ翻訳キーへの
 * `Component.translatable` を返して各クライアントの言語で描画させます。
 * 未解決IDはID原文を返します。GUI表示専用であり、検証・実行・出力の
 * ID解決には使いません。
 *
 * Bukkitの `translationKey()` はサーバー起動を要求するため使わず、
 * 解決済みキーから翻訳キー文字列を組み立てます。サーバーなし試験でも動作します。
 */
object EntityTypeDisplay {
    /**
     * 種別IDに対応する表示用Componentを返します。
     *
     * Paper Registryが初期化できないAPI単体テスト環境ではRegistry解決を
     * 飛ばし、Bukkitのenum直結で解決します。どちらでも解決できない場合は
     * ID原文を返し、表示を欠落させません。
     */
    fun componentFor(rawId: String, color: TextColor? = null): Component {
        val raw = rawId.trim()
        if (raw.isEmpty()) return Component.text(raw)
        val key = resolveKey(raw) ?: return Component.text(raw)
        val base = Component.translatable("entity.${key.namespace}.${key.key}")
        return if (color == null) base else base.color(color)
    }

    /**
     * 種別IDに対応する「表示名（ID）」形式の表示用Componentを返します。
     *
     * 一覧のホバーで名前とIDを1行にまとめて示します。IDが空欄のときは
     * 空の表示を返し、他項目の未設定表示と揃えます。
     */
    fun labeledFor(rawId: String): Component {
        val raw = rawId.trim()
        if (raw.isEmpty()) return Component.text(raw)
        return componentFor(raw).append(Component.text("（$raw）"))
    }

    // ID文字列から実在する種別のキーを解決します。BukkitのRegistryとenumの
    // 両方を使い、サーバーなし試験ではenum直結へ縮退します。名前空間違いの
    // 誤解決を避けるため、キー一致だけを受け付けます。
    private fun resolveKey(rawId: String): NamespacedKey? {
        val key = NamespacedKey.fromString(rawId) ?: return null
        runCatching { Registry.ENTITY_TYPE.get(key) }.getOrNull()?.let { return key }
        return runCatching { EntityType.valueOf(key.key.uppercase()) }.getOrNull()
            ?.takeIf { it.key == key }
            ?.key
    }
}
