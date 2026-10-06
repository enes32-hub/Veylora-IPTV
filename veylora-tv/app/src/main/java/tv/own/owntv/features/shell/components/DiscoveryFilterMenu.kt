package tv.own.owntv.features.shell.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.catalog.DiscoveryFilter
import tv.own.owntv.features.epg.GuideMenuHost
import tv.own.owntv.ui.stage.StageMenuHeader
import tv.own.owntv.ui.stage.StageMenuItem
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/** Remote-first editor: arrows navigate buttons, OK changes a value; no keyboard on focus. */
@Composable
internal fun DiscoveryFilterMenu(current: DiscoveryFilter, genres: List<String>, onApply: (DiscoveryFilter) -> Unit, onDismiss: () -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    var choosingGenre by remember { mutableStateOf(false) }
    val initialFocus = remember { FocusRequester() }
    val unlimited = stringResource(R.string.veylora_filter_any)
    LaunchedEffect(choosingGenre) { initialFocus.requestFocus() }
    GuideMenuHost(x = 920f, top = 70.mpx, width = 850.mpx, onDismiss = {
        dismissDiscoveryFilter(choosingGenre, draft, onApply, { choosingGenre = false }, onDismiss)
    }) {
        StageMenuHeader(stringResource(R.string.veylora_filters), null)
        if (choosingGenre) {
            Text(stringResource(R.string.veylora_genre_coverage), Modifier.padding(16.mpx),
                color = StageColors.Muted, style = stageText(18, 400))
            StageMenuItem(unlimited, { draft = draft.copy(genre = null); choosingGenre = false }, Modifier.focusRequester(initialFocus))
            genres.forEach { genre -> StageMenuItem(tv.own.owntv.features.discovery.genreLabel(genre), { draft = draft.copy(genre = genre); choosingGenre = false }, checked = genre == draft.genre) }
        } else {
            StageMenuItem(stringResource(R.string.veylora_filter_genre), { choosingGenre = true }, Modifier.focusRequester(initialFocus), value = draft.genre?.let { tv.own.owntv.features.discovery.genreLabel(it) } ?: unlimited)
            FilterRangeRow(stringResource(R.string.veylora_filter_min_year), draft.minYear?.toString() ?: unlimited,
                { draft = draft.copy(minYear = ((draft.minYear ?: 2001) - 1).coerceIn(1888, draft.maxYear ?: 2100)) },
                { draft = draft.copy(minYear = null) },
                { draft = draft.copy(minYear = ((draft.minYear ?: 1999) + 1).coerceIn(1888, draft.maxYear ?: 2100)) })
            FilterRangeRow(stringResource(R.string.veylora_filter_max_year), draft.maxYear?.toString() ?: unlimited,
                { draft = draft.copy(maxYear = ((draft.maxYear ?: 2001) - 1).coerceIn(draft.minYear ?: 1888, 2100)) },
                { draft = draft.copy(maxYear = null) },
                { draft = draft.copy(maxYear = ((draft.maxYear ?: 1999) + 1).coerceIn(draft.minYear ?: 1888, 2100)) })
            FilterRangeRow(stringResource(R.string.veylora_filter_min_rating), draft.minRating?.toString() ?: unlimited,
                { draft = draft.copy(minRating = ((draft.minRating ?: 5.5) - 0.5).coerceIn(0.0, draft.maxRating ?: 10.0)) },
                { draft = draft.copy(minRating = null) },
                { draft = draft.copy(minRating = ((draft.minRating ?: 4.5) + 0.5).coerceIn(0.0, draft.maxRating ?: 10.0)) })
            FilterRangeRow(stringResource(R.string.veylora_filter_max_rating), draft.maxRating?.toString() ?: unlimited,
                { draft = draft.copy(maxRating = ((draft.maxRating ?: 10.5) - 0.5).coerceIn(draft.minRating ?: 0.0, 10.0)) },
                { draft = draft.copy(maxRating = null) },
                { draft = draft.copy(maxRating = ((draft.maxRating ?: 9.5) + 0.5).coerceIn(draft.minRating ?: 0.0, 10.0)) })
            StageMenuItem(stringResource(R.string.veylora_filter_watched), {
                draft = draft.copy(watched = when (draft.watched) { null -> false; false -> true; true -> null })
            }, value = when (draft.watched) { null -> unlimited; true -> stringResource(R.string.veylora_watched); false -> stringResource(R.string.veylora_unwatched) })
            StageMenuItem(stringResource(R.string.veylora_filter_clear), { onApply(DiscoveryFilter()); onDismiss() })
        }
    }
}

internal fun dismissDiscoveryFilter(
    choosingGenre: Boolean,
    draft: DiscoveryFilter,
    onApply: (DiscoveryFilter) -> Unit,
    onBackToFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (choosingGenre) onBackToFilters() else {
        onApply(draft)
        onDismiss()
    }
}

@Composable
private fun FilterRangeRow(label: String, value: String, minus: () -> Unit, clear: () -> Unit, plus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.mpx), horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = StageColors.Text, style = stageText(20, 500))
        StageTool(text = stringResource(R.string.veylora_filter_minus), onClick = minus)
        StageTool(text = value, onClick = clear)
        StageTool(text = stringResource(R.string.veylora_filter_plus), onClick = plus)
    }
}
