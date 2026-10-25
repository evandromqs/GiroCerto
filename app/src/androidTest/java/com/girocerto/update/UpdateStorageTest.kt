package com.girocerto.update

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class UpdateStorageTest {
    @Test fun missingVerifiedApkCanBeDownloadedAgainAndProviderIsScoped() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("app_updates", 0)
        val previous = prefs.getString("release", null)
        val previousId = prefs.getLong("download_id", -1)
        val file = File(context.filesDir,"updates/GiroCerto-99.0.0.apk").apply { parentFile!!.mkdirs(); writeText("test fixture, not an APK") }
        val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
        try {
            val json = JSONObject().put("versionCode",9999).put("versionName","99.0.0").put("sha256",hash)
                .put("apkUrl","https://github.com/evandromqs/GiroCerto/releases/download/v99.0.0/GiroCerto-99.0.0.apk")
            prefs.edit().putString("release",json.toString()).remove("download_id").commit()
            val manager = withContext(Dispatchers.Main) { AppUpdateManager(context) }
            withTimeout(5000) { manager.state.first { it.phase == DownloadPhase.READY } }
            val uri = FileProvider.getUriForFile(context,"${context.packageName}.updates",file)
            assertEquals("content",uri.scheme)
            try { FileProvider.getUriForFile(context,"${context.packageName}.updates",File(context.filesDir,"private-ledger.json")); fail("Provider must not expose unrelated files") }
            catch (_: IllegalArgumentException) { }
            assertTrue(file.delete())
            withContext(Dispatchers.Main) { manager.onResume() }
            withTimeout(5000) { manager.state.first { it.phase == DownloadPhase.FAILED } }
            Unit
        } finally {
            file.delete()
            prefs.edit().apply { if(previous == null) remove("release") else putString("release",previous)
                if(previousId < 0) remove("download_id") else putLong("download_id",previousId) }.commit()
        }
    }
}
