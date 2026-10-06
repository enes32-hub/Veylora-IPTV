package tv.own.owntv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.R
import tv.own.owntv.core.metadata.CatalogScanStatus

@Composable
fun CatalogScanBanner() {
    val state by CatalogScanStatus.state.collectAsStateWithLifecycle()
    if (!state.showBanner) return
    Column(Modifier.fillMaxWidth().background(Color(0xFF10181D)).padding(horizontal = 20.dp, vertical = 5.dp)) {
        Text(stringResource(R.string.catalog_scan_running, state.completed, state.total, (state.fraction * 100).toInt()), color = Color.White, fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(state.fraction).height(3.dp).background(MaterialTheme.colorScheme.primary))
        }
    }
}
