package tv.own.owntv.features.home

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureTranslationCoverageTest {
    private fun strings(dir: File): Map<String, String> = buildMap {
        dir.listFiles { f -> f.extension == "xml" }.orEmpty().forEach { file ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                put(node.attributes.getNamedItem("name").nodeValue, node.textContent)
            }
        }
    }

    @Test fun `added catalog and playback controls have translations in every supported locale`() {
        val required = strings(File("src/main/res/values")).keys.filter { it.startsWith("veylora_") || it.startsWith("catalog_scan_") }.toSet() +
            setOf("veylora_popular_movies", "veylora_popular_series", "player_black_screen")
        val localeDirs = File("../../veylora-core/core/src/main/res").listFiles { f ->
            f.isDirectory && f.name.matches(Regex("values-[a-z]{2}(-r[A-Z]{2})?"))
        }.orEmpty()
        assertTrue("No supported locale inventory", localeDirs.size >= 25)
        val missing = localeDirs.flatMap { locale ->
            val translated = strings(File("src/main/res/${locale.name}"))
            required.filter { translated[it].isNullOrBlank() }.map { "${locale.name}:$it" }
        }
        assertTrue("Missing localized feature labels: $missing", missing.isEmpty())
    }
}
