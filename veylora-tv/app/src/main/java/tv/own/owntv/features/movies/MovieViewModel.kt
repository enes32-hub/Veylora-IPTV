@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.owntv.features.movies

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.applyCustomizations
import tv.own.owntv.core.customize.CategoryMove
import tv.own.owntv.core.customize.CategoryRailEditor
import tv.own.owntv.core.customize.MoveKind
import tv.own.owntv.core.customize.railCategories
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProgressDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.PlaybackProgressEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.util.throttleLatest
import tv.own.owntv.features.customize.MoveTarget
import tv.own.owntv.features.live.LiveRailItem
import tv.own.owntv.core.download.DownloadManager
import tv.own.owntv.core.storage.StorageAccess
import tv.own.owntv.core.storage.MediaFolders
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.features.search.positionOf
import tv.own.owntv.features.discovery.DiscoverySessionRandom
import tv.own.owntv.core.live.parseLiveKey
import tv.own.owntv.core.live.serialize
import tv.own.owntv.core.settings.SourceOverrides

class MovieViewModel(
    private val movieDao: MovieDao,
    /** Lets the browse screen tell core's drain which category to fill first (core's N1c). */
    private val catalogPriority: tv.own.owntv.core.sync.CatalogPriority,
    private val categoryDao: CategoryDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val userDataWriter: tv.own.owntv.core.backup.UserDataWriter,
    private val progressDao: ProgressDao,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val player: OwnTVPlayer,
    private val downloadManager: DownloadManager,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val contentOrderDao: ContentOrderDao,
    private val customCategoryDao: CustomCategoryDao,
    private val metadata: tv.own.owntv.core.metadata.MetadataRepository,
    private val externalPlayerLauncher: tv.own.owntv.core.player.ExternalPlayerLauncher,
    private val streamUrlResolver: tv.own.owntv.core.stalker.StreamUrlResolver,
    private val subtitleController: tv.own.owntv.core.subtitles.SubtitleController,
) : ViewModel() {

    private val scopedDiscoveryFilter = settings.activeProfileId.flatMapLatest { profileId ->
        if (profileId < 0) flowOf(profileId to tv.own.owntv.core.catalog.DiscoveryFilter())
        else settings.discoveryFilter(profileId, tv.own.owntv.core.catalog.DiscoveryMedia.MOVIE).map { profileId to it }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, -1L to tv.own.owntv.core.catalog.DiscoveryFilter())
    val discoverySelection: StateFlow<Pair<Long, tv.own.owntv.core.catalog.DiscoveryFilter>> = scopedDiscoveryFilter
    val discoveryFilter = scopedDiscoveryFilter.map { it.second }
        .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.catalog.DiscoveryFilter())
    fun setDiscoveryFilter(value: tv.own.owntv.core.catalog.DiscoveryFilter, profileId: Long = scopedDiscoveryFilter.value.first) {
        viewModelScope.launch {
            if (profileId < 0 || settings.activeProfileId.first() != profileId ||
                profileDao.resolveExistingProfileId(profileId) != profileId) return@launch
            settings.setDiscoveryFilter(profileId, tv.own.owntv.core.catalog.DiscoveryMedia.MOVIE, value)
        }
    }
    private val discoverySettings = combine(scopedDiscoveryFilter, settings.metadataConfigFlow) { scoped, config ->
        Triple(scoped.first, scoped.second, config)
    }

    private fun discoverySql(key: LiveKey, c: Ctx, query: String, sort: SettingsRepository.SortMode,
        filter: tv.own.owntv.core.catalog.DiscoveryFilter, language: String, cs: CustState, count: Boolean = false,
        metadataMode: tv.own.owntv.core.metadata.MetadataMode = tv.own.owntv.core.metadata.MetadataMode.PROVIDER_PLUS_TMDB,
        randomSeed: Long = DiscoverySessionRandom.value,
        folderKeys: Map<Long, String> = folderContextKeys.value,
        manualContexts: Set<String> = orderedContexts.value,
    ): androidx.sqlite.db.SupportSQLiteQuery? {
        val discoveryScope = when (key) {
            LiveKey.Favorites -> tv.own.owntv.core.catalog.DiscoveryScope.Favorites
            LiveKey.History -> tv.own.owntv.core.catalog.DiscoveryScope.History
            is LiveKey.Custom -> tv.own.owntv.core.catalog.DiscoveryScope.Custom(key.id)
            else -> tv.own.owntv.core.catalog.DiscoveryScope.All
        }
        val order = when (sort) {
            SettingsRepository.SortMode.POPULAR -> tv.own.owntv.core.catalog.DiscoveryOrder.POPULAR
            SettingsRepository.SortMode.RANDOM -> tv.own.owntv.core.catalog.DiscoveryOrder.RANDOM
            SettingsRepository.SortMode.RATING -> tv.own.owntv.core.catalog.DiscoveryOrder.RATING
            SettingsRepository.SortMode.DATE_ADDED -> tv.own.owntv.core.catalog.DiscoveryOrder.ADDED
            SettingsRepository.SortMode.PLAYLIST -> tv.own.owntv.core.catalog.DiscoveryOrder.PROVIDER
            else -> tv.own.owntv.core.catalog.DiscoveryOrder.TITLE
        }
        val manual = when (key) {
            is LiveKey.Folder -> folderKeys[key.id]
            is LiveKey.Custom -> key.id
            LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
            else -> null
        }
            ?.takeIf { it in manualContexts && sort == SettingsRepository.SortMode.PLAYLIST }
            ?.let { tv.own.owntv.core.catalog.DiscoveryManualOrder(c.profileId, it) }
        val sql = tv.own.owntv.core.catalog.discoveryQuery(tv.own.owntv.core.catalog.DiscoveryMedia.MOVIE,
            c.sourceIds, filter, language, (key as? LiveKey.Folder)?.id, query, order, count, cs.hiddenCats, manualOrder = manual,
            metadataMode = metadataMode, includeAdult = c.includeAdult, profileId = c.profileId, randomSeed = randomSeed, scope = discoveryScope,
            excludedItemKeys = cs.cust.hiddenItems.keys + cs.cust.movedFromOrigin.filterValues { origin ->
                key is LiveKey.Folder && origin == folderKeys[key.id]
            }.keys)
        return androidx.sqlite.db.SimpleSQLiteQuery(sql.sql, sql.arguments.toTypedArray())
    }

    /** The categories' search text, held here so it survives the player (see LiveViewModel). */
    private val _categoryQuery = kotlinx.coroutines.flow.MutableStateFlow("")
    val categoryQuery: kotlinx.coroutines.flow.StateFlow<String> = _categoryQuery
    fun setCategoryQuery(query: String) { _categoryQuery.value = query }

    data class MovieMoveState(val items: List<MovieEntity>, val activeIndex: Int, val contextKey: String)
    private val _moveState = MutableStateFlow<MovieMoveState?>(null)
    val moveState: StateFlow<MovieMoveState?> = _moveState.asStateFlow()

    /** Hide and reorder a category from the rail — the same editor Settings → Customize writes through. */
    private val categoryEditor = CategoryRailEditor(customize, categoryDao, profileDao)

    private val _categoryMoveState = MutableStateFlow<CategoryMove?>(null)
    val categoryMoveState: StateFlow<CategoryMove?> = _categoryMoveState.asStateFlow()

    private data class Ctx(
        val profileId: Long,
        val sourceIds: List<Long>,
        val sourceNames: Map<Long, String>,
        val includeAdult: Boolean = false,
    )
    // Observe the active profile's sources reactively so adding/removing a playlist refreshes Movies
    // immediately (was read once at startup, so a new playlist showed nothing until app restart).
    private val ctx: StateFlow<Ctx> = activeProfileSources(settings, sourceDao)
        .flatMapLatest { aps ->
            val ids = aps.movieSourceIds
            profileDao.observeById(aps.profileId).map { profile ->
                Ctx(aps.profileId, ids, aps.sources.filter { it.id in ids }.associate { it.id to it.name },
                    tv.own.owntv.core.metadata.profileAllowsAdultMetadata(profile?.isKids))
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(-1L, emptyList(), emptyMap()))

    val discoveryGenres = combine(ctx, settings.metadataConfigFlow) { c, config -> c to config }
        .flatMapLatest { (c, config) ->
            val sql = tv.own.owntv.core.catalog.discoveryQuery(tv.own.owntv.core.catalog.DiscoveryMedia.MOVIE,
                c.sourceIds, tv.own.owntv.core.catalog.DiscoveryFilter(minRating = null), config.resolvedLanguage, genresOnly = true,
                metadataMode = config.mode, includeAdult = c.includeAdult)
            movieDao.discoveryGenres(androidx.sqlite.db.SimpleSQLiteQuery(sql.sql, sql.arguments.toTypedArray()))
        }.map { rows -> rows.filterNotNull().flatMap { raw ->
            runCatching { val a = org.json.JSONArray(raw); (0 until a.length()).map { a.getString(it) } }.getOrDefault(emptyList())
        }.distinct().sorted() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val folderContextKeys: StateFlow<Map<Long, String>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptyMap())
            else categoryDao.observe(c.sourceIds, MediaType.MOVIE).map { cats ->
                cats.associateBy({ it.id }, { CustomizeKeys.category(it) })
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Contexts that actually have manual-order rows (C3): only those folders pay the
     *  unindexable content_order join-sort; everything else stays on the plain indexed query. */
    private val orderedContexts: StateFlow<Set<String>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptySet())
            else contentOrderDao.observeContextKeys(c.profileId, MediaType.MOVIE).map { it.toSet() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** This profile's hide/rename/reorder customizations for Movies. */
    private val custom: StateFlow<SectionCustomizations> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(SectionCustomizations())
            else customize.observe(c.profileId, MediaType.MOVIE)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SectionCustomizations())

    /** The user's custom combined categories with live member counts — the "Move to…" dialog's list. */
    val moveTargets: StateFlow<List<MoveTarget>> = combine(ctx, custom) { c, cust -> c to cust }
        .flatMapLatest { (c, cust) ->
            if (c.profileId < 0 || cust.customCategories.isEmpty()) flowOf(emptyList())
            else customCategoryDao.observeCountsByContexts(
                c.profileId,
                MediaType.MOVIE,
                cust.customCategories.map { it.id },
                c.sourceIds.ifEmpty { listOf(-1L) },
            ).map { counts ->
                cust.customCategories.map { cc ->
                    MoveTarget(
                        id = cc.id,
                        displayName = cust.categoryNames[cc.id] ?: cc.name,
                        count = counts.firstOrNull { it.contextKey == cc.id }?.count ?: 0,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The stable key of a provider folder ([null] when the folder vanished) — the Move dialog's origin. */
    fun folderKey(id: Long): String? = folderContextKeys.value[id]

    /** Creates a custom category (issue #87) — the Move dialog's "＋ New category…" flow. */
    fun createCustomCategory(name: String) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.createCustomCategory(pid, MediaType.MOVIE, name)
        }
    }

    /**
     * Moves (or copies, [keepInOrigin]) one movie into a custom category (issue #87). The item is
     * appended at the category's tail (maxPosition + 1). Without [keepInOrigin] the item leaves its
     * origin: a favorite row is deleted, a custom-category membership row is deleted, and a provider
     * folder is marked in movedFromOrigin — the pager chain then drops it from that folder while
     * keeping it in All / search / recent.
     */
    fun moveToCategory(itemKey: String, itemId: Long, originKey: String, targetId: String, keepInOrigin: Boolean) {
        if (targetId == originKey) return
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customCategoryDao.appendItem(pid, MediaType.MOVIE, targetId, itemId)
            if (!keepInOrigin) {
                when {
                    originKey == ContentOrderEntity.FAV_CONTEXT -> userDataWriter.removeFavorite(pid, MediaType.MOVIE, itemId)
                    CustomizeKeys.isCustom(originKey) -> userDataWriter.removeCustomCategoryMember(pid, MediaType.MOVIE, originKey, itemId)
                    else -> customize.setItemMovedFromOrigin(pid, MediaType.MOVIE, itemKey, originKey, moved = true)
                }
            }
        }
    }

    /**
     * Category DB ids of this profile's hidden Movie categories — so hiding a category hides its
     * movies everywhere (All, search, Home rails), not just the rail folder (mirrors Live TV).
     */
    private val hiddenCategoryIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) {
                flowOf(emptySet())
            } else {
                combine(categoryDao.observe(c.sourceIds, MediaType.MOVIE), custom, profileDao.observeById(c.profileId)) { cats, cust, profile ->
                    tv.own.owntv.core.content.AdultCategoryClassifier.hiddenCategoryIds(
                        cats,
                        cust.hiddenCategories,
                        profile?.isKids == true,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Customizations + resolved hidden-category ids, bundled so the list pipeline takes one flow. */
    private data class CustState(val cust: SectionCustomizations, val hiddenCats: Set<Long>)
    private val custResolved: StateFlow<CustState> = combine(custom, hiddenCategoryIds) { c, h -> CustState(c, h) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustState(SectionCustomizations(), emptySet()))

    /** List ordering for this section (Provider order vs A–Z), persisted in DataStore. */
    val sortMode: StateFlow<SettingsRepository.SortMode> = settings.sortMovies
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.SortMode.PLAYLIST)

    fun setSort(mode: SettingsRepository.SortMode) {
        if (mode == SettingsRepository.SortMode.RANDOM) {
            refreshList()
        }
        viewModelScope.launch { settings.setSortMovies(mode) }
    }

    val viewMode: StateFlow<SettingsRepository.VodViewMode> = settings.vodViewMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.VodViewMode.GRID)

    fun toggleViewMode() {
        viewModelScope.launch {
            settings.setVodViewMode(
                if (viewMode.value == SettingsRepository.VodViewMode.GRID) SettingsRepository.VodViewMode.LIST
                else SettingsRepository.VodViewMode.GRID,
            )
        }
    }

    private val _selected = MutableStateFlow<LiveKey>(LiveKey.All)
    val selectedKey: StateFlow<LiveKey> = _selected.asStateFlow()

    // Bumped after a favourite/history mutation so the pager rebuilds its (manual, non-reactive)
    // PagingSource. Without this, unfavouriting on the Favorites category (or removing from History)
    // can leave the removed movie in the paged snapshot, which breaks focus restore (the stale row
    // disposes under focus). Behaviour is intermittent because Room's invalidation timing varies.
    private val _listRefresh = MutableStateFlow(0)
    private fun refreshList() { _listRefresh.value++ }

    private val _search = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _search.asStateFlow()

    private val _selectedMovie = MutableStateFlow<MovieEntity?>(null)
    val selectedMovie: StateFlow<MovieEntity?> = _selectedMovie.asStateFlow()

    /**
     * On-demand TMDB enrichment for the focused movie (plan §7.2: detail screens resolve lazily). Debounced
     * so scrolling fast doesn't fire a lookup per card; cached in Room so a second focus is instant. Null
     * when enrichment is off or no confident match — the UI then shows pure provider data (§7.1).
     */
    /** Bumped by [refetchMovieMeta] to force the focused movie's TMDB resolve to re-run after clearing its cache. */
    private val _metaRefreshTick = MutableStateFlow(0L)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    /** TMDB details already resolved this session, by title id: shown without the focus debounce. */
    private val resolvedMeta = java.util.concurrent.ConcurrentHashMap<Long, tv.own.owntv.core.database.entity.MetadataCacheEntity>()
    private var resolvedMetaScope: Pair<Ctx, tv.own.owntv.core.metadata.MetadataConfig>? = null

    val selectedMovieMeta: StateFlow<MovieMeta?> = combine(_selectedMovie, _metaRefreshTick, ctx, settings.metadataConfigFlow) { m, tick, c, config ->
        Triple(m, tick, c to config)
    }
        .distinctUntilChanged { a, b -> a.first?.id == b.first?.id && a.second == b.second && a.third == b.third }
        // 700 ms, not 350: sustained D-pad scrolling was firing a lookup per card it passed over, which
        // made browsing the single biggest source of metadata traffic. At 700 ms a scroll produces one
        // lookup when the user actually settles on something.
        // A title resolved once this session shows at once; only an unseen one waits the focus debounce
        // (FOCUS_DEBOUNCE_MS: scrolling past cards must cost nothing), then reads the DB cache or TMDB.
        .transformLatest { (m, _, scope) ->
            emit(null)
            if (resolvedMetaScope != scope) {
                resolvedMeta.clear()
                resolvedMetaScope = scope
            }
            if (m == null || !scope.second.enabled || m.sourceId !in scope.first.sourceIds) return@transformLatest
            resolvedMeta[m.id]?.let { emit(MovieMeta(m.id, it)); return@transformLatest }
            val cache = try {
                tv.own.owntv.features.discovery.focusedMetadata(
                    cached = { metadata.cachedMoviePresentation(listOf(m))[m.id] },
                    waitForFocus = { delay(tv.own.owntv.core.metadata.MetadataRepository.FOCUS_DEBOUNCE_MS) },
                    resolve = { metadata.resolveMovie(m) },
                )
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch (_: Exception) { null }
            if (settings.metadataConfig() != scope.second) return@transformLatest
            if (cache != null) {
                if (resolvedMeta.size > 500) resolvedMeta.clear()
                resolvedMeta[m.id] = cache
            }
            emit(MovieMeta(m.id, cache))
            presentationCache.selectScope(scope)
            presentationCache.update(listOf(m.id), cache?.let { mapOf(m.id to it) }.orEmpty())
            _cachedPresentation.value = tv.own.owntv.features.discovery.LocalizedPresentation(scope.second.resolvedLanguage,
                presentationCache.snapshot(), presentationCache.checkedIds())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** TMDB metadata tagged with the movie id it was resolved for, so the UI never shows stale meta on a
     *  different card during the debounce window. [cache] is null while resolving or on no match. */
    data class MovieMeta(val movieId: Long, val cache: tv.own.owntv.core.database.entity.MetadataCacheEntity?)

    /**
     * Poster fallback for grid/list tiles the provider gave no artwork for. Those show a placeholder
     * even once the detail pane has resolved and cached a TMDB poster for the same title — most
     * visibly under Date added, which puts a whole freshly-imported batch at the front.
     *
     * The screen reports the on-screen posterless items and gets back whatever the cache already
     * holds. Cache-only by design: a DB read, never a TMDB call, so scrolling costs no quota.
     */
    private val _posterProbe = MutableStateFlow<List<MovieEntity>>(emptyList())
    val effectiveValues = combine(ctx, settings.metadataConfigFlow, _posterProbe, _selectedMovie) { c, config, visible, selected ->
        Triple(c, config, (visible.map { it.id } + listOfNotNull(selected?.id)).distinct())
    }.flatMapLatest { (c, config, ids) ->
        val sql = tv.own.owntv.core.catalog.discoveryQuery(tv.own.owntv.core.catalog.DiscoveryMedia.MOVIE,
            c.sourceIds, tv.own.owntv.core.catalog.DiscoveryFilter(minRating = null), config.resolvedLanguage,
            metadataMode = config.mode, includeAdult = c.includeAdult, itemIds = ids, valuesOnly = true)
        movieDao.discoveryValues(androidx.sqlite.db.SimpleSQLiteQuery(sql.sql, sql.arguments.toTypedArray()))
    }.map { rows -> rows.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val metadataConfig = settings.metadataConfigFlow.stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.metadata.MetadataConfig())
    private val _cachedPresentation = MutableStateFlow(tv.own.owntv.features.discovery.LocalizedPresentation())
    private val presentationCache = tv.own.owntv.features.discovery.StablePresentationCache<tv.own.owntv.core.database.entity.MetadataCacheEntity>()
    val cachedPresentation = _cachedPresentation.asStateFlow()

    fun onPosterlessVisible(movies: List<MovieEntity>) { _posterProbe.value = movies }

    /** Source mode (plan §4.1) — the detail pane uses it to flip provider/TMDB field precedence. */
    val metadataMode: StateFlow<tv.own.owntv.core.metadata.MetadataMode> = settings.metadataMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.metadata.MetadataMode.PROVIDER_PLUS_TMDB)

    // Observable so the player HUD's favorite toggle can reflect/act on the movie being played.
    private val _playingMovie = MutableStateFlow<MovieEntity?>(null)
    val playingMovie: StateFlow<MovieEntity?> = _playingMovie.asStateFlow()

    /**
     * What we handed the player, pinned at [play] time — the only thing a resume position may be
     * written against.
     *
     * Progress used to be matched by comparing the player's current URL against the movie's stored
     * `streamUrl`, and the profile was read at save time. Both were wrong: a Stalker playback URL is
     * minted per play and never equals the stored cmd, so those movies never saved a position at all;
     * and switching profile mid-film wrote the position into the *new* profile's Continue Watching.
     */
    private data class PlayingRef(val movie: MovieEntity, val profileId: Long, val contentKey: String?)

    private var playingRef: PlayingRef? = null

    init {
        // Periodically persist resume position for the movie currently playing. This is the crash
        // backstop; the real saves happen on pause (below) and on leaving the player.
        viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                saveProgressNow()
            }
        }
        // Save on pause too — otherwise pausing and walking away loses up to 10s, and everything
        // since the last tick if the app is killed while paused.
        viewModelScope.launch {
            var wasPlaying = false
            player.isPlaying.collect { playing ->
                if (wasPlaying && !playing) saveProgressNow()
                wasPlaying = playing
            }
        }
        // Resolve the poster fallback for what is on screen. Debounced so a fast scroll issues one
        // query when it settles; keyed on the refresh tick too, so a "Refetch TMDB details" that
        // dropped an entry re-reads it instead of leaving the tile blank.
        viewModelScope.launch {
            val cache = presentationCache
            combine(_posterProbe, _metaRefreshTick, ctx, settings.metadataConfigFlow) { probe, _, c, config ->
                Triple(probe, c, config)
            }
                .debounce(250)
                .collectLatest { (probe, c, config) ->
                    if (cache.selectScope(c to config)) {
                        _cachedPresentation.value = tv.own.owntv.features.discovery.LocalizedPresentation(config.resolvedLanguage)
                    }
                    if (!config.enabled) return@collectLatest
                    val wanted = probe.filter { it.sourceId in c.sourceIds }
                    if (wanted.isEmpty()) return@collectLatest
                    val loaded = try { metadata.cachedMoviePresentation(wanted).filterValues {
                        it.key == tv.own.owntv.core.metadata.MetadataRepository.cacheKey(it.tmdbId, config.resolvedLanguage)
                    } }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { return@collectLatest }
                    val matched = metadata.matchedMovieIds(wanted)
                    if (settings.metadataConfig() != config) return@collectLatest
                    cache.update(wanted.map { it.id }, loaded, matched - loaded.keys)
                    val presentation = cache.snapshot()
                    _cachedPresentation.value = tv.own.owntv.features.discovery.LocalizedPresentation(config.resolvedLanguage, presentation, cache.checkedIds())
                }
        }
    }

    val railItems: StateFlow<List<LiveRailItem>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(defaultRail)
            else combine(
                categoryDao.observe(c.sourceIds, MediaType.MOVIE),
                customize.observe(c.profileId, MediaType.MOVIE),
                sortMode,
                profileDao.observeById(c.profileId),
            ) { cats, cust, sort, profile ->
                // A–Z also sorts the category folders (custom categories included); manually moved
                // categories stay pinned first. Custom categories ride the SAME customization keys,
                // so renames/hides/reorders apply to them with no extra code (#87).
                val folders = cats.railCategories(
                    cust,
                    kids = profile?.isKids == true,
                    alphaRest = sort == SettingsRepository.SortMode.ALPHA,
                )
                val multiSourceNames = c.sourceNames.takeIf { it.size > 1 }.orEmpty()
                val categoriesById = cats.associateBy { it.id }
                defaultRail + folders.map { e ->
                    LiveRailItem(
                        key = e.categoryId?.let { LiveKey.Folder(it) } ?: LiveKey.Custom(e.customId!!),
                        title = e.displayName,
                        providerName = e.categoryId
                            ?.let(categoriesById::get)
                            ?.sourceId
                            ?.let(multiSourceNames::get),
                        sourceId = e.categoryId?.let(categoriesById::get)?.sourceId,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultRail)

    val movies: Flow<PagingData<MovieEntity>> = combine(
        _selected, ctx, _search.map { it.trim() }.debounce(300).distinctUntilChanged(), sortMode, _listRefresh,
    ) { key, c, query, sort, _ -> Args(key, c, query, sort) }
        .combine(DiscoverySessionRandom.sessionSeed) { args, seed -> args.copy(randomSeed = seed) }
        .combine(discoverySettings) { args, config ->
            args.copy(filter = config.second, language = config.third.resolvedLanguage, discoveryProfileId = config.first,
                metadataMode = config.third.mode)
        }
        .combine(custResolved) { args, cs -> args to cs }
        // Rebuild the pager when a folder gains/loses manual order (C3): the fast-path plain
        // PagingSource doesn't observe content_order, so the switch must recreate it.
        .combine(orderedContexts) { p, _ -> p }
        .combine(folderContextKeys) { p, _ -> p }
        .flatMapLatest { (args, cs) ->
            if (args.ctx.profileId < 0 || args.ctx.profileId != args.discoveryProfileId) return@flatMapLatest flowOf(tv.own.owntv.features.shell.components.pendingVodPage<MovieEntity>())
            // Hidden items/categories are filtered on each fresh PagingData inside the pager chain —
            // a customization change re-creates the pager (same pattern as Live TV).
            Pager(PagingConfig(pageSize = 60, prefetchDistance = 30, initialLoadSize = 90, maxSize = 300)) {
                discoverySql(args.key, args.ctx, args.query, args.sort, args.filter, args.language, cs, metadataMode = args.metadataMode, randomSeed = args.randomSeed)
                    ?.let { sql ->
                        movieDao.discoveryPage(sql)
                    } ?: pagingSource(args.key, args.ctx, args.query, args.sort)
            }.flow.map { paging ->
                val cust = cs.cust
                val movedFrom = cust.movedFromOrigin
                if (cust.hiddenItems.isEmpty() && cust.itemNames.isEmpty() && cs.hiddenCats.isEmpty() && movedFrom.isEmpty()) paging
                else paging.filter { m ->
                    movieVisible(args.key, m, cs)
                }.map { m ->
                    // Bulk-renamed titles (Customize items screen) show here like Live TV does.
                    cust.itemNames[CustomizeKeys.movie(m)]?.let { m.copy(name = it) } ?: m
                }
            }
        }
        .cachedIn(viewModelScope)

    private fun movieVisible(key: LiveKey, movie: MovieEntity, cs: CustState,
        folderKeys: Map<Long, String> = folderContextKeys.value): Boolean =
        CustomizeKeys.movie(movie) !in cs.cust.hiddenItems &&
            (movie.categoryId == null || movie.categoryId !in cs.hiddenCats) &&
            (cs.cust.movedFromOrigin[CustomizeKeys.movie(movie)]?.let { origin ->
                key !is LiveKey.Folder || origin != folderKeys[key.id]
            } ?: true)

    private data class Args(val key: LiveKey, val ctx: Ctx, val query: String, val sort: SettingsRepository.SortMode,
        val randomSeed: Long = 1,
        val filter: tv.own.owntv.core.catalog.DiscoveryFilter = tv.own.owntv.core.catalog.DiscoveryFilter(), val language: String = "",
        val discoveryProfileId: Long = -1L,
        val metadataMode: tv.own.owntv.core.metadata.MetadataMode = tv.own.owntv.core.metadata.MetadataMode.PROVIDER_PLUS_TMDB)

    val count: StateFlow<Int> = combine(_selected, ctx, custResolved) { key, c, cs -> Triple(key, c, cs) }
        .combine(discoverySettings) { scope, filter -> scope to filter }
        .combine(_search) { state, query -> Triple(state.first, state.second, query) }
        .combine(sortMode) { state, sort -> state to sort }
        .flatMapLatest { (state, sort) ->
            val (scope, config, query) = state
            val (key, c, cs) = scope
            if (c.profileId != config.first) return@flatMapLatest flowOf(0)
            (discoverySql(key, c, query, sort, config.second, config.third.resolvedLanguage, cs, true, metadataMode = config.third.mode)
                ?.let(movieDao::discoveryCount) ?: countFlow(key, c, cs.hiddenCats)).throttleLatest()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The count beside every category (Stage sheet / column): one grouped query for the folders. */
    val railCounts: StateFlow<Map<LiveKey, Int>> = combine(ctx, hiddenCategoryIds, railItems) { c, hidden, items -> Triple(c, hidden, items) }
        .flatMapLatest { (c, hidden, items) ->
            val single = items.filter { it.key !is LiveKey.Folder }.map { item -> countFlow(item.key, c, hidden).map { mapOf(item.key to it) } }
            val folders = movieDao.observeCountsByCategory(c.sourceIds.ifEmpty { listOf(-1L) })
                .map { rows -> rows.associate { LiveKey.Folder(it.categoryId) as LiveKey to it.itemCount } }
            combine(single + folders) { parts -> parts.fold(emptyMap<LiveKey, Int>()) { acc, m -> acc + m } }.throttleLatest()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Which playlist a title or category comes from (GOLD / JR), only while more than one is shown. */
    val playlistMarks: StateFlow<Map<Long, tv.own.owntv.ui.stage.PlaylistMark>> = activeProfileSources(settings, sourceDao)
        .map { aps ->
            if (aps.movieSourceIds.size < 2) emptyMap()
            else aps.sources.withIndex().associate { (i, s) -> s.id to tv.own.owntv.ui.stage.PlaylistMark.of(s.name, i) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Whether "Remove from history" has anything to remove for [movie]. */
    suspend fun isInHistory(movie: MovieEntity): Boolean {
        val pid = currentProfileId() ?: return false
        return historyDao.exists(pid, MediaType.MOVIE, movie.id)
    }

    val favoriteIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.MOVIE) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Resume/watched progress for the visible movies, keyed by movie id — drives the ✓ tick and the
     *  in-progress bar on posters/list rows. Only started/finished movies have a row, so this is small. */
    val movieProgress: StateFlow<Map<Long, PlaybackProgressEntity>> = ctx
        .flatMapLatest { c -> if (c.profileId < 0) flowOf(emptyList()) else progressDao.observeMovieProgress(c.profileId) }
        .map { list -> list.associateBy { it.itemId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val selectedProgress: StateFlow<PlaybackProgressEntity?> = combine(_selectedMovie, ctx) { m, c -> m to c }
        .flatMapLatest { (m, c) ->
            if (m == null) flowOf(null) else progressDao.observe(c.profileId, MediaType.MOVIE, m.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Set while this view model is serving More -> Favourites or More -> History rather than the
     * browse section. Those screens are one folder each: the selection is theirs to fix, and it must
     * not be persisted as the remembered category.
     */
    private var lockedKey: LiveKey? = null

    /** What the browse section was showing before the pin, so [unlock] can put it back. */
    private var previousKey: LiveKey? = null

    fun lock(key: LiveKey) {
        if (lockedKey == null) previousKey = _selected.value
        lockedKey = key
        _selected.value = key
    }

    /**
     * Release the pin and put the browse section back where it was.
     *
     * **This is not optional bookkeeping.** On the television this view model is a single instance
     * shared between the browse section and the More screens, so a pin that outlived the screen that
     * took it froze the section's category rail — focusable, but every click a no-op. The screen that
     * locks therefore unlocks on dispose.
     */
    fun unlock() {
        val previous = previousKey ?: return
        lockedKey = null
        previousKey = null
        _selected.value = previous
    }

    fun select(key: LiveKey) {
        if (lockedKey != null) return
        _selected.value = key
        // Core's lazy-catalogue drain fills categories in the provider's order; tell it the user is
        // here so this one is served next (core's N1c). A no-op for every playlist that was not
        // lazily added, and for a category that is already complete.
        if (key is LiveKey.Folder) {
            viewModelScope.launch { runCatching { catalogPriority.requestFirst(key.id) } }
        } else {
            catalogPriority.clear()
        }
    }
    fun setSearchQuery(query: String) { _search.value = query }
    fun onMovieFocused(movie: MovieEntity) { _selectedMovie.value = movie }

    /** Search's "Go to movie": the titles scroll to [reveal] and focus it, then call [revealDone]. */
    private val _reveal = MutableStateFlow<tv.own.owntv.features.search.Reveal?>(null)
    val reveal: StateFlow<tv.own.owntv.features.search.Reveal?> = _reveal.asStateFlow()

    /** Open [movie] where it lives: its own category, or All movies when that category is not in the
     *  rail or the movie is not in it. It becomes the selected title (Cinematic's top shows it). */
    fun revealMovie(movie: MovieEntity, openDetails: Boolean = false) {
        _search.value = ""
        _reveal.value = null
        viewModelScope.launch {
            val c = ctx.first { it.profileId >= 0 }
            val config = discoverySettings.first { it.first == c.profileId }
            val snapshot = movieRevealSnapshot(
                customize.observe(c.profileId, MediaType.MOVIE),
                categoryDao.observe(c.sourceIds, MediaType.MOVIE),
                profileDao.observeById(c.profileId),
                contentOrderDao.observeContextKeys(c.profileId, MediaType.MOVIE),
            ) ?: return@launch
            if (settings.activeProfileId.first() != c.profileId || !snapshot.profile.isKids != c.includeAdult) return@launch
            val cs = CustState(snapshot.custom, snapshot.hiddenCategories)
            if (movie.sourceId !in c.sourceIds || !movieVisible(LiveKey.All, movie, cs, snapshot.folderKeys)) return@launch
            if (openDetails) {
                _selectedMovie.value = movie
                _revealDetails.value = movie
                return@launch
            }
            val sort = settings.sortMovies.first()
            val folder = movie.categoryId?.let { LiveKey.Folder(it) }
            val inRail = folder != null && snapshot.categories.railCategories(snapshot.custom,
                kids = snapshot.profile.isKids, alphaRest = sort == SettingsRepository.SortMode.ALPHA)
                .any { it.categoryId == folder.id }
            for (key in lockedKey?.let { listOf(it) } ?: listOfNotNull(folder.takeIf { inRail }, LiveKey.All)) {
                val source = discoverySql(key, c, "", sort, config.second, config.third.resolvedLanguage,
                    cs, metadataMode = config.third.mode, folderKeys = snapshot.folderKeys,
                    manualContexts = snapshot.orderedContexts)?.let { sql ->
                        movieDao.discoveryPage(sql)
                    }
                    ?: pagingSource(key, c, "", sort)
                val position = source.positionOf(movie.id, { it.id }, isVisible = { movieVisible(key, it, cs, snapshot.folderKeys) }) ?: continue
                if (ctx.value != c || scopedDiscoveryFilter.value != (config.first to config.second)) return@launch
                _selectedMovie.value = movie
                if (lockedKey == null) select(key)
                _reveal.value = tv.own.owntv.features.search.Reveal(key, movie.id, position)
                return@launch
            }
            // A search hit outside the saved discovery filter opens its details. The grid and saved
            // filter remain intact; no fabricated position or silent filter reset is emitted.
            if (ctx.value == c && scopedDiscoveryFilter.value == (config.first to config.second)) {
                _selectedMovie.value = movie
                _revealDetails.value = movie
            }
        }
    }

    fun revealDone() { _reveal.value = null }
    private val _revealDetails = MutableStateFlow<MovieEntity?>(null)
    val revealDetails = _revealDetails.asStateFlow()
    fun revealDetailsDone() { _revealDetails.value = null }

    /**
     * Manual "Refetch TMDB details" (plan §11.2 U5a): clear this movie's cached match/details (incl. a 7-day
     * negative cache) and re-trigger [resolveMovie] for the focused movie via the meta-refresh tick.
     */
    fun refetchMovieMeta(movie: MovieEntity) {
        viewModelScope.launch {
            runCatching { metadata.clearMovie(movie) }
            resolvedMeta.remove(movie.id)
            _cachedPresentation.value = _cachedPresentation.value.copy(rows = _cachedPresentation.value.rows - movie.id)
            _metaRefreshTick.value++
        }
    }

    /**
     * Prefill for the "Set TMDB name" dialog (plan §11.2 U5b): the saved override if any, else the cleaned
     * provider title. [hasOverride] drives the dialog's Clear button.
     */
    data class TmdbNamePrefill(val title: String, val year: Int?, val hasOverride: Boolean)

    suspend fun movieTmdbNamePrefill(movie: MovieEntity): TmdbNamePrefill {
        metadata.movieOverride(movie)?.let { return TmdbNamePrefill(it.title, it.year, hasOverride = true) }
        val norm = tv.own.owntv.core.metadata.TitleNormalizer.normalize(movie.name)
        return TmdbNamePrefill(norm.query, movie.year ?: norm.year, hasOverride = false)
    }

    /** Save the hand-typed override and force a re-resolve under the new query (plan §11.2 U5b). */
    fun setMovieTmdbName(movie: MovieEntity, title: String, year: Int?) {
        viewModelScope.launch {
            runCatching { metadata.setMovieOverride(movie, title, year) }
            resolvedMeta.remove(movie.id)
            _metaRefreshTick.value++
        }
    }

    /** Remove the override and re-resolve with the cleaned provider title (plan §11.2 U5b). */
    fun clearMovieTmdbName(movie: MovieEntity) {
        viewModelScope.launch {
            runCatching { metadata.clearMovieOverride(movie) }
            resolvedMeta.remove(movie.id)
            _metaRefreshTick.value++
        }
    }

    /** The user's resume preference (Always / Ask / Never) — the screen drives the prompt. */
    val resumeMode: StateFlow<SettingsRepository.ResumeMode> = settings.resumeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.ResumeMode.ASK)

    /** Saved resume position for [movie] (0 when none) — used by the screen to decide the prompt. */
    suspend fun savedPositionMs(movie: MovieEntity): Long =
        currentProfileId()?.let { progressDao.get(it, MediaType.MOVIE, movie.id)?.positionMs ?: 0 } ?: 0

    /** Global "External player" toggle — screens must NOT open the fullscreen in-app player when on
     *  (mounting it spins up an mpv instance even though play() branched to the external app). */
    val externalPlayerOn: StateFlow<Boolean> = settings.externalPlayerMovies
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Stalker movies resolve to a real URL at play time; anything else returns streamUrl as-is.
     *  Null = resolve failed (portal/auth error) — the caller should not start playback. */
    private suspend fun resolvedUrlOrNull(movie: MovieEntity): String? {
        val source = sourceDao.getById(movie.sourceId)
        if (!streamUrlResolver.needsResolve(source)) return movie.streamUrl
        return try {
            streamUrlResolver.resolve(source!!, movie.streamUrl, vod = true)
        } catch (e: Exception) {
            Log.w(TAG, "stalker resolve failed movieId=${movie.id}", e)
            null
        }
    }

    /** Phase B: long-press "Play with external player" — always external, regardless of the global toggle. */
    fun playExternal(movie: MovieEntity) {
        viewModelScope.launch {
            val pid = currentProfileId()
            if (pid != null && !tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, movie.categoryId, profileDao, categoryDao)) return@launch
            Log.d(TAG, "playExternal movieId=${movie.id}")
            val url = resolvedUrlOrNull(movie) ?: return@launch
            val source = sourceDao.getById(movie.sourceId)
            val title = moviePlaybackTitle(movie.name, { settings.metadataConfig() }, { metadata.resolveMovie(movie) })
                ?: return@launch
            externalPlayerLauncher.launch(
                url = url,
                title = title,
                userAgent = source?.userAgent,
                httpHeaders = SourceOverrides.headersWithReferer(movie.httpHeaders, source),
            )
            if (pid != null) {
                runCatching {
                    historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.MOVIE, itemId = movie.id))
                }.onFailure { t -> Log.w(TAG, "external play history record failed movieId=${movie.id} profile=$pid", t) }
            }
        }
    }

    fun play(movie: MovieEntity, startPositionMs: Long = 0) {
        viewModelScope.launch {
            val pid = currentProfileId()
            if (pid != null && !tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, movie.categoryId, profileDao, categoryDao)) return@launch
            // External player (global toggle): hand the stream URL to an external app and skip the
            // in-app engine entirely. History is still recorded (recently-watched); resume position
            // and the playing-movie HUD/progress tick are intentionally not — the external app owns
            // playback and OwnTV can't observe it.
            // #115 — a protected item cannot go to an external player: no standard intent extra
            // carries a licence URL, so the other app would open it and fail on the first segment.
            // Play it here instead, where the licence request can actually be made.
            if (settings.externalPlayerMovies.first() && movie.drmConfig == null) {
                Log.d(TAG, "play movieId=${movie.id} -> external player")
                val url = resolvedUrlOrNull(movie) ?: return@launch
                val source = sourceDao.getById(movie.sourceId)
                val title = moviePlaybackTitle(movie.name, { settings.metadataConfig() }, { metadata.resolveMovie(movie) })
                    ?: return@launch
                externalPlayerLauncher.launch(
                    url = url,
                    title = title,
                    userAgent = source?.userAgent,
                    httpHeaders = SourceOverrides.headersWithReferer(movie.httpHeaders, source),
                )
                if (pid != null) {
                    runCatching {
                        historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.MOVIE, itemId = movie.id))
                    }.onFailure { t -> Log.w(TAG, "external play history record failed movieId=${movie.id} profile=$pid", t) }
                }
                return@launch
            }
            val source = sourceDao.getById(movie.sourceId)
            val sourceUa = source?.userAgent
            // Stalker: mint the playable URL right before playback (create_link, type=vod). The stored
            // streamUrl stays the cmd — it's the item's identity (engine pins, downloads, history).
            val playUrl = if (streamUrlResolver.needsResolve(source)) {
                try {
                    streamUrlResolver.resolve(source!!, movie.streamUrl, vod = true)
                } catch (e: Exception) {
                    Log.w(TAG, "stalker resolve failed movieId=${movie.id}", e)
                    return@launch
                }
            } else {
                movie.streamUrl
            }
            Log.d(TAG, "play movieId=${movie.id} profile=$pid startPositionMs=$startPositionMs")
            val pinKey = tv.own.owntv.core.player.enginePinKey(movie.sourceId, "MOVIE", movie.remoteId)
            val title = moviePlaybackTitle(movie.name, { settings.metadataConfig() }, { metadata.resolveMovie(movie) })
                ?: return@launch
            player.play(
                playUrl,
                title = title,
                year = movie.year?.toString(),
                isLive = false,
                startPositionMs = startPositionMs,
                userAgent = sourceUa,
                httpHeaders = SourceOverrides.headersWithReferer(movie.httpHeaders, source),
                vodEngineOverride = SourceOverrides.vodEngineOf(source),
                drmConfig = movie.drmConfig,
                manifestType = movie.manifestType,
                // P6 — engine pins key on this, not on playUrl (a Stalker playUrl is minted per play).
                contentKey = pinKey,
                // F12 — a Stalker create_link URL dies before a long film ends; give the player a way to
                // mint a fresh one instead of retrying the expired link. Null for M3U/Xtream, which also
                // clears any provider the previous item left on the player.
                reconnectProvider = if (streamUrlResolver.needsResolve(source)) {
                    tv.own.owntv.core.stalker.ReconnectUrlProvider {
                        runCatching { streamUrlResolver.resolve(source!!, movie.streamUrl, vod = true) }
                            .onFailure { Log.w(TAG, "stalker VOD reconnect resolve failed movieId=${movie.id}", it) }
                            .getOrNull()
                    }
                } else null,
            )
            _playingMovie.value = movie
            playingRef = pid?.let { PlayingRef(movie, it, pinKey) }
            // Enable the player's OpenSubtitles search for this movie (subtitle plan §4). tmdbId is
            // resolved from the metadata cache when available (review R7) for a stronger match.
            if (pid != null) {
                val tmdbId = runCatching { metadata.resolveMovie(movie)?.tmdbId?.toLong() }.getOrNull()
                subtitleController.setMovie(pid, movie, tmdbId)
            }
            if (pid != null) {
                runCatching {
                    historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.MOVIE, itemId = movie.id))
                }.onFailure { t ->
                    Log.w(TAG, "play history record failed movieId=${movie.id} profile=$pid", t)
                }
            }
        }
    }

    fun playById(movieId: Long, startPositionMs: Long = 0) {
        viewModelScope.launch {
            val movie = movieDao.getById(movieId) ?: return@launch
            play(movie, startPositionMs)
        }
    }

    suspend fun playByIdAsync(movieId: Long, startPositionMs: Long = 0): Boolean {
        val movie = movieDao.getById(movieId) ?: return false
        play(movie, startPositionMs)
        return true
    }

    /** Download states for the currently visible movies, keyed by movie id. */
    val downloadStates: StateFlow<Map<Long, DownloadEntity>> = ctx
        .flatMapLatest { c -> if (c.profileId < 0) flowOf(emptyList()) else downloadManager.observe(c.profileId) }
        .map { list -> list.filter { it.mediaType == MediaType.MOVIE }.associateBy { it.itemId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun download(movie: MovieEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, movie.categoryId, profileDao, categoryDao)) return@launch
            downloadManager.enqueue(
                profileId = pid,
                mediaType = MediaType.MOVIE,
                itemId = movie.id,
                title = movie.name,
                posterUrl = movie.posterUrl,
                streamUrl = movie.streamUrl,
                relativeDir = MediaFolders.MOVIES,
                fileName = "${StorageAccess.sanitize(movie.name)}.${movie.containerExt ?: StorageAccess.extOf(movie.streamUrl)}",
            )
        }
    }

    /** Downloaded OpenSubtitles subtitles for this movie (long-press "Delete subtitles" popup, §11). */
    suspend fun downloadedSubtitles(movie: MovieEntity): List<tv.own.owntv.core.database.dao.LinkedSubtitle> =
        subtitleController.downloadsForMovie(movie)

    fun deleteSubtitle(cacheId: Long) {
        viewModelScope.launch { subtitleController.deleteCached(cacheId) }
    }

    fun toggleFavorite(movie: MovieEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (favoriteIds.value.contains(movie.id)) userDataWriter.removeFavorite(pid, MediaType.MOVIE, movie.id)
            else favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.MOVIE, itemId = movie.id))
            refreshList() // the Favorites category uses a manual PagingSource — force a rebuild
        }
    }

    /** Hide a category by its rail key (undo via Settings → Customize). */
    fun hideCategory(key: LiveKey) {
        viewModelScope.launch {
            categoryEditor.hide(currentProfileId() ?: return@launch, MediaType.MOVIE, key)
        }
    }

    fun enterCategoryMoveMode(key: LiveKey) {
        if (key !is LiveKey.Folder && key !is LiveKey.Custom) return
        viewModelScope.launch {
            val c = ctx.first { it.profileId >= 0 }
            _categoryMoveState.value = categoryEditor.beginMove(
                profileId = c.profileId,
                sourceIds = c.sourceIds,
                type = MediaType.MOVIE,
                alphaRest = sortMode.value == SettingsRepository.SortMode.ALPHA,
                key = key,
            )
        }
    }

    fun moveCategoryUp() {
        _categoryMoveState.value = _categoryMoveState.value?.moved(MoveKind.UP) ?: return
    }

    fun moveCategoryDown() {
        _categoryMoveState.value = _categoryMoveState.value?.moved(MoveKind.DOWN) ?: return
    }

    fun commitCategoryMove() {
        val s = _categoryMoveState.value ?: return
        _categoryMoveState.value = null
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.setCategoryOrder(pid, MediaType.MOVIE, s.keys)
        }
    }

    fun cancelCategoryMove() {
        _categoryMoveState.value = null
    }

    /** ≥95% of duration watched = completed (mirrors SeriesViewModel.isEpisodeCompleted). */
    fun isMovieCompleted(p: PlaybackProgressEntity): Boolean =
        p.durationMs > 0 && p.positionMs >= (p.durationMs * 0.95f).toLong()

    /** Mark a movie as watched (shows ✓) without playing it — same synthetic 1ms/1ms sentinel trick used
     *  for episodes (satisfies the ≥95% completed rule while keeping Play restarting from ~0). */
    fun markMovieWatched(movie: MovieEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            progressDao.save(
                PlaybackProgressEntity(profileId = pid, mediaType = MediaType.MOVIE, itemId = movie.id, positionMs = 1L, durationMs = 1L),
            )
        }
    }

    /** Mark a movie as unwatched — clears its resume position (removes the ✓ and any progress bar). */
    fun markMovieUnwatched(movie: MovieEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            userDataWriter.clearProgress(pid, MediaType.MOVIE, movie.id)
        }
    }

    /**
     * True while the player still holds the movie [ref] was pinned for. Matches on the stable engine
     * pin key; rows with no `remoteId` have no such key and fall back to the stream URL, which for
     * those rows is exactly as stable as it always was (see `enginePinKey`).
     */
    private fun playerIsOn(ref: PlayingRef): Boolean =
        if (ref.contentKey != null) player.currentMediaContentKey == ref.contentKey
        else player.currentMediaUrl != null && player.currentMediaUrl == ref.movie.streamUrl

    /** Persist the resume position if the player is still on the movie we started. */
    fun saveProgressNow() {
        val ref = playingRef ?: return
        val m = ref.movie
        if (player.isLiveContent || !playerIsOn(ref)) return
        val pos = player.position.value
        val dur = player.duration.value
        if (pos > 0 && dur > 0) {
            viewModelScope.launch {
                // The position belongs to the profile that started playback. If the user switched
                // profiles mid-film, drop it rather than writing it into the new profile's list.
                val pid = currentProfileId() ?: return@launch
                if (pid != ref.profileId) return@launch
                Log.d(TAG, "saveProgressNow movieId=${m.id} profile=$pid positionMs=$pos durationMs=$dur")
                runCatching {
                    progressDao.save(
                        PlaybackProgressEntity(profileId = pid, mediaType = MediaType.MOVIE, itemId = m.id, positionMs = pos, durationMs = dur),
                    )
                }.onFailure { t ->
                    Log.w(TAG, "saveProgressNow progress save failed movieId=${m.id} profile=$pid", t)
                }
                launcherIntegrationRepository.publishMovieProgress(pid, m.id, pos, dur)
            }
        }
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    fun enterMoveMode(movie: MovieEntity, key: LiveKey) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            val contextKey = when (key) {
                is LiveKey.Folder -> folderContextKeys.value[key.id] ?: return@launch
                is LiveKey.Custom -> key.id
                LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                else -> return@launch
            }
            val items = when (key) {
                is LiveKey.Folder -> movieDao.snapshotByCategoryManual(key.id, pid, contextKey, 5000)
                is LiveKey.Custom -> customCategoryDao.snapshotMovies(pid, key.id, ctx.value.sourceIds.ifEmpty { listOf(-1L) }, 5000)
                LiveKey.Favorites -> movieDao.snapshotFavoritesManual(pid, contextKey, ctx.value.sourceIds.ifEmpty { listOf(-1L) }, 5000)
                LiveKey.History, LiveKey.All -> return@launch
            }
            val idx = items.indexOfFirst { it.id == movie.id }
            if (idx < 0) return@launch
            _moveState.value = MovieMoveState(items, idx, contextKey)
            // Manual order is only visible in playlist order, so Move switches the list to it — but that
            // is a means, not a choice the user made. Remember what they had so Cancel can put it back.
            sortBeforeMove = sortMode.value
            settings.setSortMovies(SettingsRepository.SortMode.PLAYLIST)
        }
    }

    /** The sort the user was on before [enterMoveMode] switched the list to playlist order. */
    private var sortBeforeMove: SettingsRepository.SortMode? = null

    fun moveUp() {
        val s = _moveState.value ?: return
        if (s.activeIndex == 0) return
        val list = s.items.toMutableList()
        val i = s.activeIndex
        list[i - 1] = s.items[i]; list[i] = s.items[i - 1]
        _moveState.value = s.copy(items = list, activeIndex = i - 1)
    }

    fun moveDown() {
        val s = _moveState.value ?: return
        if (s.activeIndex == s.items.size - 1) return
        val list = s.items.toMutableList()
        val i = s.activeIndex
        list[i + 1] = s.items[i]; list[i] = s.items[i + 1]
        _moveState.value = s.copy(items = list, activeIndex = i + 1)
    }

    fun commitMove() {
        val s = _moveState.value ?: return
        _moveState.value = null
        sortBeforeMove = null // the new order IS playlist order — staying on it is the point
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            contentOrderDao.replaceContext(
                profileId = pid,
                type = MediaType.MOVIE,
                contextKey = s.contextKey,
                rows = s.items.mapIndexed { i, m ->
                    ContentOrderEntity(profileId = pid, mediaType = MediaType.MOVIE, contextKey = s.contextKey, itemId = m.id, position = i)
                },
            )
        }
    }

    fun cancelMove() {
        _moveState.value = null
        // Cancel means nothing changed — including the sort Move switched away from.
        val previous = sortBeforeMove ?: return
        sortBeforeMove = null
        if (previous != SettingsRepository.SortMode.PLAYLIST) {
            viewModelScope.launch { settings.setSortMovies(previous) }
        }
    }

    /** Hide the movie from all lists (undo via Settings → Customize Category → Hidden items). */
    fun hideMovie(movie: MovieEntity) {
        if (_selectedMovie.value?.id == movie.id) _selectedMovie.value = null
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.setItemHidden(pid, MediaType.MOVIE, CustomizeKeys.movie(movie), movie.name, true)
        }
    }

    fun removeFromHistory(movieId: Long) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            userDataWriter.removeHistory(pid, MediaType.MOVIE, movieId)
            userDataWriter.clearProgress(pid, MediaType.MOVIE, movieId)
            refreshList() // the History category uses a manual PagingSource — force a rebuild
        }
    }

    private fun pagingSource(key: LiveKey, c: Ctx, query: String, sort: SettingsRepository.SortMode): PagingSource<Int, MovieEntity> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        val playlist = sort == SettingsRepository.SortMode.PLAYLIST
        val rating = sort == SettingsRepository.SortMode.RATING
        val dateAdded = sort == SettingsRepository.SortMode.DATE_ADDED
        return if (query.isBlank()) when (key) {
            // Catch-up is a Live TV-only rail (channels have archives, movies don't), but the rail model
            // is shared across all three sections — so it degrades to All here rather than existing.
            LiveKey.All, LiveKey.Catchup -> when {
                rating -> movieDao.pagingAllRating(ids)
                playlist -> movieDao.pagingAllOriginal(ids)
                dateAdded -> movieDao.pagingAllDateAdded(ids)
                else -> movieDao.pagingAll(ids)
            }
            LiveKey.Favorites -> movieDao.pagingFavoritesManual(c.profileId, ContentOrderEntity.FAV_CONTEXT, ids)
            LiveKey.History -> movieDao.pagingHistory(c.profileId, ids)
            is LiveKey.Custom -> customCategoryDao.pagingMovies(c.profileId, key.id, ids)
            is LiveKey.Folder -> {
                val ctxKey = folderContextKeys.value[key.id] ?: ""
                when {
                    rating -> movieDao.pagingByCategoryRating(key.id)
                    dateAdded -> movieDao.pagingByCategoryDateAdded(key.id)
                    // C3 fast path: no manual order in this folder → the plain indexed query has
                    // the identical (sortOrder, name) order without the join-sort.
                    ctxKey !in orderedContexts.value -> movieDao.pagingByCategory(key.id)
                    else -> movieDao.pagingByCategoryManual(key.id, c.profileId, ctxKey)
                }
            }
        } else when (key) {
            LiveKey.All, LiveKey.Catchup ->
                if (dateAdded) movieDao.searchAllDateAdded(query, ids)
                else movieDao.searchAll(query, ids)
            LiveKey.Favorites -> movieDao.searchFavorites(query, c.profileId, ids)
            LiveKey.History -> movieDao.searchHistory(query, c.profileId, ids)
            is LiveKey.Custom -> customCategoryDao.searchMovies(query, c.profileId, key.id, ids)
            is LiveKey.Folder ->
                if (dateAdded) movieDao.searchInCategoryDateAdded(query, key.id)
                else movieDao.searchInCategory(query, key.id)
        }
    }

    private fun countFlow(key: LiveKey, c: Ctx, hiddenCats: Set<Long>): Flow<Int> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        return when (key) {
            LiveKey.All, LiveKey.Catchup ->
                if (hiddenCats.isEmpty()) movieDao.countAll(ids)
                else movieDao.countAllExcluding(ids, hiddenCats.toList())
            LiveKey.Favorites -> movieDao.countFavorites(c.profileId, ids)
            LiveKey.History -> movieDao.countHistory(c.profileId, ids)
            is LiveKey.Custom -> customCategoryDao.countMembers(c.profileId, MediaType.MOVIE, key.id, ids)
            is LiveKey.Folder -> movieDao.countByCategory(key.id)
        }
    }

    // Remember the last selected category (Settings → Browsing & lists → "Remember last category —
    // Movies", on by default). Declared LAST in the class so railItems below/above is already assigned
    // when this init runs. Mirrors LiveViewModel's identical block.
    init {
        // Persist on change, debounced — the rail fires select() on focus as you scroll it.
        viewModelScope.launch {
            _selected.drop(1).debounce(800).distinctUntilChanged()
                .collect { if (lockedKey == null) settings.setLastMoviesCategory(it.serialize()) }
        }
        // Restore once at startup, and only while still on the default (never yank a user who already
        // navigated). A saved folder is honoured only once it exists in this profile's rail.
        // A new application session starts at All. The existing ViewModel retains a selection
        // while switching sections; persisted categories are intentionally not restored.
    }

    private companion object {
        const val TAG = "OwnTVHome"
        val defaultRail = listOf(
            LiveRailItem(LiveKey.Favorites, icon = OwnTVIcon.FAVORITE),
            LiveRailItem(LiveKey.History, icon = OwnTVIcon.HISTORY),
            LiveRailItem(LiveKey.All),
        )
    }
}
