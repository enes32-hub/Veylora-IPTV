package tv.own.owntv.features.shell.components

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.theme.mpx

internal enum class VodEmptyState { LOADING, EMPTY, ERROR }

/** Status for a catalogue with no presented items. */
internal fun vodEmptyState(refresh: LoadState, settledEmpty: Boolean = false): VodEmptyState = when (refresh) {
    LoadState.Loading -> if (settledEmpty) VodEmptyState.EMPTY else VodEmptyState.LOADING
    is LoadState.Error -> VodEmptyState.ERROR
    is LoadState.NotLoading -> VodEmptyState.EMPTY
}

/** Clear the old profile's items without claiming that the new profile has an empty library. */
internal fun <T : Any> pendingVodPage(): PagingData<T> = PagingData.empty(
    sourceLoadStates = LoadStates(LoadState.Loading, LoadState.NotLoading(false), LoadState.NotLoading(false)),
)

@Composable
internal fun VodEmptyContent(refresh: LoadState, emptyText: String, onRetry: () -> Unit, modifier: Modifier = Modifier, queryKey: Any? = emptyText) {
    var settledEmpty by remember(queryKey) { mutableStateOf(false) }
    SideEffect { if (refresh is LoadState.NotLoading) settledEmpty = true }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.mpx)) {
        when (vodEmptyState(refresh, settledEmpty)) {
            VodEmptyState.LOADING -> {
                OwnTVSpinner(sizeDp = 36)
                Text(stringResource(R.string.settings_loading), style = stageText(20, 500), color = StageColors.Muted)
            }
            VodEmptyState.EMPTY -> Text(emptyText, style = stageText(20, 500), color = StageColors.Muted)
            VodEmptyState.ERROR -> {
                Text(stringResource(R.string.veylora_catalog_load_failed), style = stageText(20, 500), color = StageColors.Muted)
                StageTool(text = stringResource(R.string.veylora_catalog_retry), onClick = { settledEmpty = false; onRetry() })
            }
        }
    }
}
