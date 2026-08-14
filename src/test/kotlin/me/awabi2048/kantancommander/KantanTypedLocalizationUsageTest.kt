package me.awabi2048.kantancommander

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KantanTypedLocalizationUsageTest {
    @Test
    fun `fixed localization references use generated typed keys`() {
        val sourceRoot = Path.of("src/main/kotlin")
        val literalCall = Regex(
            """KcI18n\.(?:text|list|component)\(\s*[^,]+,\s*\"[^\"$]+\"""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        val violations = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.extension == "kt" }
                .filter { literalCall.containsMatchIn(it.readText()) }
                .map(sourceRoot::relativize)
                .sorted()
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "固定言語キーはKcKeysのLocalizationKey定数で参照してください: $violations",
        )
    }
}
