package tv.own.owntv.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.core.storage.StorageAccess
import tv.own.owntv.core.storage.SelectedFileImport
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import java.io.File

enum class BrowseMode { FOLDER, FILE }

/** One directory's contents, already split and sorted off the main thread (audit U2). */
private data class Listing(val folders: List<File>, val files: List<File>) {
    companion object { val EMPTY = Listing(emptyList(), emptyList()) }
}

/**
 * Uses system-selected documents, copying file imports into private persistent storage.
 * Folder callers supporting document URIs use SAF; the TV fallback exposes only app-owned roots.
 */
@Composable
fun StorageBrowser(
    title: String,
    mode: BrowseMode,
    onPick: (File) -> Unit,
    onDismiss: () -> Unit,
    fileExtensions: Set<String>? = null,
    onPickDocumentRoot: ((Uri) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pick by rememberUpdatedState(onPick)
    val dismiss by rememberUpdatedState(onDismiss)
    var fallback by remember { mutableStateOf(false) }
    var requestSystemPicker by remember { mutableStateOf(mode == BrowseMode.FOLDER) }
    val sourceFocus = remember { FocusRequester() }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) dismiss() else scope.launch {
            var pendingImport: File? = null
            try {
                val imported = withContext(Dispatchers.IO) {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: uri.lastPathSegment.orEmpty()
                    val stream = context.contentResolver.openInputStream(uri) ?: error("Missing selected document")
                    val jobContext = currentCoroutineContext()
                    val cancellable = object : java.io.FilterInputStream(stream) {
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            jobContext.ensureActive()
                            return super.read(buffer, offset, length)
                        }
                    }
                    cancellable.use {
                        SelectedFileImport.copy(File(context.filesDir, "selected-documents"), name, fileExtensions, it)
                            .also { file -> pendingImport = file }
                    }
                }
                pick(imported)
                pendingImport = null
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) { pendingImport?.delete() }
                throw cancelled
            } catch (_: Exception) {
                withContext(NonCancellable + Dispatchers.IO) { pendingImport?.delete() }
                Toast.makeText(context, R.string.storage_selection_failed, Toast.LENGTH_LONG).show()
                dismiss()
            }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        // Some TVs resolve OPEN_DOCUMENT_TREE to a stub that returns no folder.
        // Offer private storage after cancellation too, rather than leaving no usable route.
        if (uri == null) fallback = true
        else if (StorageAccess.persistAccess(context, uri)) onPickDocumentRoot?.invoke(uri)
        else {
            Toast.makeText(context, R.string.storage_selection_failed, Toast.LENGTH_LONG).show()
            fallback = true
        }
    }
    LaunchedEffect(requestSystemPicker) {
        if (!requestSystemPicker) return@LaunchedEffect
        try {
            if (mode == BrowseMode.FILE) filePicker.launch(arrayOf("*/*"))
            else if (onPickDocumentRoot != null) folderPicker.launch(null)
            else fallback = true
        } catch (_: ActivityNotFoundException) {
            if (mode == BrowseMode.FILE) {
                Toast.makeText(context, R.string.storage_picker_unavailable, Toast.LENGTH_LONG).show()
            }
            fallback = true
        }
    }
    if (mode == BrowseMode.FILE && !fallback && !requestSystemPicker) {
        tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = title, eyebrow = null, width = 900.mpx) {
            BrowserRow(OwnTVIcon.PLAYLIST, stringResource(R.string.content_key_browse), Modifier.focusRequester(sourceFocus)) {
                requestSystemPicker = true
            }
            BrowserRow(OwnTVIcon.DOWNLOADS, stringResource(R.string.content_storage_app)) { fallback = true }
        }
        LaunchedEffect(Unit) { sourceFocus.requestFocus() }
    }
    if (!fallback) return
    // Hosted in a real window: D-pad focus physically cannot escape to the screen behind. An
    // inline overlay loses focus containment when rows are added/removed (the grant-access row
    // after returning from system settings) and Compose reassigns focus outside the trap.
    // "New folder" is opened beside this popup, never inside it: a popup nested in another inherits the
    // first one's already-applied popup theme, so it skipped the user's popup font and size settings.
    var createIn by remember { mutableStateOf<((String) -> Unit)?>(null) }
    // Back is the browser's own (it climbs a folder first), so the popup does not close on it.
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = null, eyebrow = null, width = 900.mpx, scroll = false, dismissOnBackPress = false) {
        StorageBrowserContent(title, mode, onPick, onDismiss, fileExtensions, onNewFolder = { createIn = it })
    }
    createIn?.let { create ->
        NewFolderDialog(
            onCreate = { name -> create(name); createIn = null },
            onDismiss = { createIn = null },
        )
    }
}

@Composable
private fun StorageBrowserContent(
    title: String,
    mode: BrowseMode,
    onPick: (File) -> Unit,
    onDismiss: () -> Unit,
    fileExtensions: Set<String>?,
    /** Ask for a folder name; the lambda creates it in the folder shown now. */
    onNewFolder: (onCreate: (String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val colors = OwnTVTheme.colors
    val roots = remember { StorageAccess.appRoots(context) }
    fun permitted(file: File): Boolean = runCatching {
        val path = file.canonicalFile.toPath()
        roots.any { path.startsWith(it.file.canonicalFile.toPath()) }
    }.getOrDefault(false)
    fun parent(file: File): File? = file.parentFile?.takeIf { permitted(it) }
    var current by remember { mutableStateOf<File?>(null) } // null = the roots list
    var refresh by remember { mutableIntStateOf(0) }
    val firstFocus = remember { FocusRequester() }

    BackHandler { if (current != null) current = current?.let(::parent) else onDismiss() }

    // Re-grab focus whenever the listing changes (open / navigate / refresh). Deferred a beat: the
    // clicked row is removed in the same recompose, and its focus teardown lands AFTER an immediate
    // request — which would leave focus on whatever sits behind the overlay.
    LaunchedEffect(current, refresh) {
        kotlinx.coroutines.delay(120)
        runCatching { firstFocus.requestFocus() }
    }


        // scroll = false: the listing below is a height-capped LazyColumn, which cannot nest inside
        // dialogPanel's own vertical scroll.
    Column(Modifier.fillMaxWidth()) {
            Text(title, style = tv.own.owntv.ui.theme.stageText(34, 800), color = tv.own.owntv.ui.theme.StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(current?.absolutePath ?: stringResource(R.string.setup_pick_location), style = tv.own.owntv.ui.theme.stageText(16, 500), color = tv.own.owntv.ui.theme.StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.mpx, bottom = 18.mpx))

            val dir = current
            // U2 — listFiles() plus the per-child isDirectory/isFile stats are disk work, and a
            // `remember` block still runs it on the main thread during composition: a USB drive with
            // a large folder stalled the frame. Load it on IO instead; the ".." / roots rows render
            // immediately either way, so D-pad focus still lands the moment the dialog opens.
            val listing by produceState(Listing.EMPTY, dir, refresh, mode, fileExtensions) {
                value = Listing.EMPTY
                value = withContext(Dispatchers.IO) {
                    val children = runCatching { dir?.listFiles()?.filter(::permitted) }.getOrNull().orEmpty()
                    Listing(
                        folders = children.filter { it.isDirectory }.sortedBy { it.name.lowercase() },
                        files = if (mode == BrowseMode.FILE) {
                            children.filter { it.isFile && (fileExtensions == null || it.extension.lowercase() in fileExtensions) }
                                .sortedBy { it.name.lowercase() }
                        } else emptyList(),
                    )
                }
            }
            val folders = listing.folders
            val files = listing.files

            // Above the list, not below it. Below, the only way down to it was through every folder
            // in the listing — about thirty presses in a full directory, for the one action the user
            // opened a folder picker to perform. Above, it is a single Up from the first row no
            // matter how long the listing is, and the list still opens focused so navigating first
            // costs nothing.
            if (mode == BrowseMode.FOLDER && current != null) {
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.setup_use_folder), onClick = { current?.let(onPick) }, icon = OwnTVIcon.CHECK, height = 56.mpx, textSize = 19, tinted = true, modifier = Modifier.fillMaxWidth().padding(bottom = 10.mpx))
            }

            // Cap the list to the screen (minus dialog chrome) so the footer buttons stay reachable.
            LazyColumn(Modifier.heightIn(max = 520.mpx).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.mpx)) {
                if (dir == null) {
                    itemsIndexed(roots) { i, root ->
                        val m = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier
                        BrowserRow(OwnTVIcon.DOWNLOADS, root.displayLabel(), m) { current = root.file }
                    }
                } else {
                    item { BrowserRow(OwnTVIcon.BACK, stringResource(R.string.setup_from_current_folder), Modifier.focusRequester(firstFocus)) { current = parent(dir) } }
                    itemsIndexed(folders) { _, f -> BrowserRow(OwnTVIcon.DOWNLOADS, f.name) { current = f } }
                    itemsIndexed(files) { _, f -> BrowserRow(OwnTVIcon.PLAYLIST, f.name) { onPick(f) } }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 22.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (current != null) tv.own.owntv.ui.stage.StageButton(stringResource(R.string.setup_new_folder), onClick = {
                    onNewFolder { name ->
                        current?.let { dir -> runCatching {
                            val child = File(dir, StorageAccess.sanitize(name))
                            if (permitted(child)) child.mkdirs()
                        } }
                        refresh++
                    }
                }, icon = OwnTVIcon.ADD, height = 56.mpx, textSize = 19)
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            }
        }
}

@Composable
private fun NewFolderDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    // A popup window of its own: drawn in the browser's layer, the browser's focus trap kept the cursor
    // behind this dialog and the name could never be typed. Deferred a beat so the window is attached.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120)
        runCatching { focus.requestFocus() }
    }
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = null, eyebrow = null, width = 756.mpx) {
        Text(stringResource(R.string.setup_new_folder), style = tv.own.owntv.ui.theme.stageText(38, 800), color = tv.own.owntv.ui.theme.StageColors.Text, modifier = Modifier.padding(bottom = 22.mpx))
        OwnTVTextField(name, { name = it }, label = stringResource(R.string.setup_folder_name), placeholder = stringResource(R.string.setup_folder_example), modifier = Modifier.fillMaxWidth().focusRequester(focus), surface = GlassSurface.DIALOGS)
        Row(Modifier.fillMaxWidth().padding(top = 24.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx, Alignment.End)) {
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_create), onClick = { if (name.isNotBlank()) onCreate(name) }, height = 56.mpx, textSize = 19, tinted = true)
        }
    }
}

@Composable
private fun StorageAccess.StorageRoot.displayLabel(): String = when (kind) {
    StorageAccess.RootKind.INTERNAL -> stringResource(R.string.content_storage_internal)
    StorageAccess.RootKind.REMOVABLE -> volumeName ?: stringResource(R.string.content_storage_removable)
    StorageAccess.RootKind.APP -> stringResource(R.string.content_storage_app)
}

@Composable
private fun BrowserRow(icon: OwnTVIcon, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    tv.own.owntv.ui.stage.StagePopupOption(
        title = label, onClick = onClick, modifier = modifier,
        leading = { tv.own.owntv.ui.stage.StagePopupIcon(icon) },
    )
}
