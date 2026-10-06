package tv.own.owntv.features.movies

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ProfileEntity

/** One initialized snapshot, loaded independently of whether the Movies screen has subscribers. */
internal data class MovieRevealSnapshot(
    val custom: SectionCustomizations,
    val categories: List<CategoryEntity>,
    val profile: ProfileEntity,
    val orderedContexts: Set<String>,
) {
    val hiddenCategories: Set<Long> = AdultCategoryClassifier.hiddenCategoryIds(categories, custom.hiddenCategories, profile.isKids)
    val folderKeys: Map<Long, String> = categories.associate { it.id to CustomizeKeys.category(it) }
}

/** Inputs must be the underlying flows for the requested profile, never StateFlow placeholders. */
internal suspend fun movieRevealSnapshot(
    custom: Flow<SectionCustomizations>,
    categories: Flow<List<CategoryEntity>>,
    profile: Flow<ProfileEntity?>,
    orderedContexts: Flow<List<String>>,
): MovieRevealSnapshot? = combine(custom, categories, profile, orderedContexts) { cust, cats, p, order ->
    p?.let { MovieRevealSnapshot(cust, cats, it, order.toSet()) }
}.first()
