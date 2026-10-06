package tv.own.owntv.features.discovery

import org.junit.Assert.assertEquals
import org.junit.Test

class GenreLabelsTest {
    @Test fun switchesLabelsWithoutChangingStoredFilterValues() {
        val labels = GenreLabels("""{"en-US":{"27":"Horror","12":"Adventure"},"de":{"27":"Horror","12":"Abenteuer"},"ar":{"27":"رعب"}}""")
        assertEquals("Abenteuer", labels.label("Adventure", "de-DE"))
        assertEquals("رعب", labels.label("Horror", "ar"))
        assertEquals("Adventure", labels.label("Abenteuer", "ar"))
        assertEquals("Adventure", labels.label("Adventure", "xx"))
    }
}
