package tv.own.owntv.core.storage

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.R

class ScopedStorageDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun installedManifestDoesNotRequestBroadFileOrInstallPermissions() {
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        for (name in listOf("MANAGE_EXTERNAL_STORAGE", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "REQUEST_INSTALL_PACKAGES")) {
            assertFalse(name, "android.permission.$name" in permissions)
        }
    }

    @Test fun privateImportAndDownloadWorkWithoutStoragePermission() {
        val root = java.io.File(context.cacheDir, "scoped-storage-test").apply { mkdirs() }
        try {
            val selected = SelectedFileImport.copy(root, "test.m3u", setOf("m3u"), ByteArrayInputStream("#EXTM3U".toByteArray()))
            assertEquals("#EXTM3U", selected.readText())
            val target = MediaRoot.Path(root).child("Movies", "test.ts") as MediaTarget.Path
            target.file.writeBytes(byteArrayOf(1, 2, 3))
            assertEquals(3L, target.file.length())
            assertTrue(StorageAccess.appRoots(context).isNotEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun privacyAndFilePickerLabelsResolveInDifferentScripts() {
        val expected = mapOf("tr" to "Gizlilik politikası", "de" to "Datenschutzerklärung", "ar" to "سياسة الخصوصية", "ja" to "プライバシーポリシー", "zh-CN" to "隐私政策")
        expected.forEach { (tag, title) ->
            val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(tag))) }
            val localized = context.createConfigurationContext(config)
            assertEquals(tag, title, localized.getString(R.string.about_privacy_policy))
            assertNotEquals(tag, context.createConfigurationContext(Configuration(config).apply { setLocale(Locale.ENGLISH) }).getString(R.string.storage_picker_unavailable), localized.getString(R.string.storage_picker_unavailable))
        }
    }
}
