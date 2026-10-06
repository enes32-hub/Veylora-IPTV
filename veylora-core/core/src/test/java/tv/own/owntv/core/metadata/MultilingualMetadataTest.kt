package tv.own.owntv.core.metadata

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.MetadataCacheEntity

class MultilingualMetadataTest {
    private val body = """{"title":"English title","overview":"English plot",
      "translations":{"translations":[
        {"iso_639_1":"tr","iso_3166_1":"TR","data":{"title":"Türkçe ad","overview":""}},
        {"iso_639_1":"pt","iso_3166_1":"BR","data":{"title":"Brasil","overview":"BR plot"}},
        {"iso_639_1":"pt","iso_3166_1":"PT","data":{"title":"Portugal","overview":"PT plot"}}]},
      "images":{"posters":[
        {"iso_639_1":"en","file_path":"/en.jpg","vote_average":5},
        {"iso_639_1":"tr","file_path":"/tr.jpg","vote_average":8},
        {"iso_639_1":null,"file_path":"/neutral.jpg","vote_average":10}],
        "backdrops":[{"iso_639_1":"en","file_path":"/en-back.jpg"}],"logos":[]}}
    """
    private fun base() = MetadataCacheEntity("movie:en:42",42,null,"movie","English title",1975,
        "English plot","/root.jpg",null,8.0,"[\"Drama\"]","[]",null,null,1L)
    private fun bundle() = MultilingualMetadata.pack(JSONObject(body))!!

    @Test fun previousLanguageAndOriginalAreNeverMissingTranslationFallbacks() {
        val stale = base().copy(title="Alter deutscher Titel", overview="Alter Text", posterPath="/old-de.jpg", originalLanguage="de")
        val payload = """{"version":2,"texts":[{"language":"de","title":"Deutsch","overview":"Deutsch"}],
            "posters":{"de":"/de.jpg"},"backdrops":{},"logos":{}}"""
        val result = MultilingualMetadata.localize(stale, payload, "ar")
        assertEquals("", result.title)
        assertNull(result.overview)
        assertNull(result.posterPath)
    }

    @Test fun filteredArtworkMustNotBeTreatedAsCompleteForEveryLanguage() {
        // The old appended English response has no evidence that other languages were fetched.
        val payload = JSONObject(bundle())
        assertFalse(payload.optInt("version") >= 2)
    }

    @Test fun unfilteredArtworkOverridesEnglishAppendAndSurvivesLanguageSwitch() {
        val allImages = JSONObject("""{"posters":[
          {"iso_639_1":"de","file_path":"/german.jpg"},
          {"iso_639_1":"en","file_path":"/english.jpg"}],"backdrops":[],"logos":[]}""")
        val payload = MultilingualMetadata.pack(JSONObject(body), allImages)!!
        assertEquals("/german.jpg", MultilingualMetadata.localize(base(), payload, "de-DE").posterPath)
        assertEquals("/english.jpg", MultilingualMetadata.localize(base(), payload, "ja-JP").posterPath)
        assertTrue(MultilingualMetadata.isComplete(MultilingualMetadata.stored(base(), payload)))
        assertFalse(MultilingualMetadata.isComplete(MultilingualMetadata.stored(base(), bundle())))
    }

    @Test fun repairingArtworkKeepsExistingIdentityTranslationsAndCommonFields() {
        val old = MultilingualMetadata.stored(base(), bundle())
        val images = JSONObject("""{"posters":[{"iso_639_1":"de","file_path":"/de.jpg"}],"backdrops":[],"logos":[]}""")
        val repaired = MultilingualMetadata.repairArtwork(old, images)!!
        val german = MultilingualMetadata.restore(repaired, "de-DE")!!
        assertEquals(42, german.tmdbId)
        assertEquals(1975, german.year)
        assertEquals("/de.jpg", german.posterPath)
        assertEquals("Türkçe ad", MultilingualMetadata.restore(repaired, "tr-TR")!!.title)
        assertTrue(MultilingualMetadata.isComplete(repaired))
        assertNull(MultilingualMetadata.repairArtwork(old, JSONObject("{}")))
    }

    @Test fun keepsTranslatedTitleButFallsBackPerMissingField() {
        val result = MultilingualMetadata.localize(base(), bundle(), "tr-TR")
        assertEquals("Türkçe ad",result.title)
        assertEquals("English plot",result.overview)
        assertEquals("/tr.jpg",result.posterPath)
        assertEquals("/en-back.jpg",result.backdropPath)
    }
    @Test fun unknownLanguageUsesEnglishBeforeNeutralImages() {
        val result = MultilingualMetadata.localize(base(),bundle(),"ja-JP")
        assertEquals("English title",result.title)
        assertEquals("English plot",result.overview)
        assertEquals("/en.jpg",result.posterPath)
    }
    @Test fun regionAndTvNamesAreResolvedLocallyFromSameBundle() {
        assertEquals("Portugal",MultilingualMetadata.localize(base(),bundle(),"pt-PT").title)
        assertEquals("Brasil",MultilingualMetadata.localize(base(),bundle(),"pt-BR").title)
        val tv = JSONObject(body.replace("\"title\"", "\"name\""))
        assertEquals("Türkçe ad",MultilingualMetadata.localize(base().copy(type="tv"),MultilingualMetadata.pack(tv)!!,"tr").title)
    }
    @Test fun incompleteResponseCannotMarkMultilingualScanComplete() {
        assertNull(MultilingualMetadata.pack(JSONObject("{}")))
        assertNull(MultilingualMetadata.pack(JSONObject("{\"translations\":{\"translations\":[]}}")))
    }
    @Test fun missingEnglishDoesNotUseOriginalTranslationAndRejectsSvgArtwork() {
        val root = JSONObject(body)
        root.put("overview", "")
        root.getJSONObject("translations").getJSONArray("translations").put(JSONObject("""
          {"iso_639_1":"fr","data":{"overview":"French original plot"}}
        """))
        root.getJSONObject("images").put("logos",org.json.JSONArray("""[
          {"iso_639_1":"en","file_path":"/logo.svg","vote_average":10},
          {"iso_639_1":"en","file_path":"/logo.png","vote_average":1}]
        """))
        val result = MultilingualMetadata.localize(base().copy(originalLanguage="fr"),MultilingualMetadata.pack(root)!!,"ja")
        assertNull(result.overview)
        assertEquals("/logo.png",result.logoPath)
    }
    @Test fun serializedBundlePreservesCommonFieldsAndLanguageFallbackAcrossRestarts() {
        val stored = MultilingualMetadata.stored(base(),bundle())
        val result = MultilingualMetadata.restore(stored,"tr-TR")!!
        assertEquals("movie:tr-TR:42",result.key)
        assertEquals("movie",result.type)
        assertEquals("English plot",result.overview)
        assertEquals(1975,result.year)
        assertEquals("[]",result.castJson)
        assertEquals("Türkçe ad",result.title)
        assertEquals("English title",MultilingualMetadata.restore(stored,"de")!!.title)
    }
}
