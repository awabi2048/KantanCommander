package me.awabi2048.kantancommander

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KantanTypedLocalizationUsageTest {
    @Test
    fun `localization references cannot leave the generated typed key boundary`() {
        val sourceRoot = Path.of("src/main/kotlin")
        val forbidden = listOf(
            Regex("""KcI18n\.(?:text|list|component)\(\s*[^,]+,\s*\"""", RegexOption.DOT_MATCHES_ALL),
            Regex("""KcI18n\.dynamic(?:Text|List|Component)\("""),
            Regex("""getI18n(?:String|StringList|Component|ComponentList)\(\s*[^,]+,\s*(?:\"|[^,\n]+\.id\b)"""),
            Regex("""LocalizationKey\.(?:text|textList)\("""),
        )
        val violations = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.extension == "kt" }
                .filter { path -> forbidden.any { it.containsMatchIn(path.readText()) } }
                .map(sourceRoot::relativize)
                .sorted()
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "生成済みLocalizationKeyを失う文字列APIまたは任意キー生成が残っています: $violations",
        )
    }
}
