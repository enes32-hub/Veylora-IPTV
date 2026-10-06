package tv.own.owntv.features.home

import tv.own.owntv.core.model.HomeConfig
import tv.own.owntv.core.model.HomeRow
import tv.own.owntv.core.model.HomeTrendingStyle

/** Add poster discovery above the existing home sections without removing profile preferences. */
internal fun lightHomeConfig(current: HomeConfig): HomeConfig = current.copy(
    hidden = current.hidden - HomeRow.TRENDING,
    trendingStyle = HomeTrendingStyle.POSTERS,
)
