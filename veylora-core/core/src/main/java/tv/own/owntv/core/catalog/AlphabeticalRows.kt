package tv.own.owntv.core.catalog

import androidx.room.Embedded
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity

data class AlphabeticalMovie(@Embedded val item: MovieEntity, val metadataTitle: String?) {
    fun displayTitle(providerTitle: String = item.name): String = metadataTitle ?: providerTitle
}
data class AlphabeticalSeries(@Embedded val item: SeriesEntity, val metadataTitle: String?) {
    fun displayTitle(providerTitle: String = item.name): String = metadataTitle ?: providerTitle
}
