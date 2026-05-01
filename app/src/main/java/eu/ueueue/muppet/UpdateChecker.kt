package eu.ueueue.muppet

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun checkAndPrompt(activity: AppCompatActivity, onStatus: (String) -> Unit) {
        try {
            val release = fetchLatestRelease() ?: return
            val publishedAt = release.get("published_at")?.asString ?: return
            val releaseTime: Long = dateFmt.parse(publishedAt)?.time ?: return

            AppLogger.log("Update", "BUILD_TIME=${BuildConfig.BUILD_TIME} releaseTime=$releaseTime")

            if (releaseTime <= BuildConfig.BUILD_TIME) {
                AppLogger.log("Update", "application à jour")
                return
            }

            val apkUrl: String = findApkUrl(release) ?: run {
                AppLogger.err("Update", "aucun APK dans la release")
                return
            }

            AppLogger.log("Update", "nouvelle version disponible → $apkUrl")

            withContext(Dispatchers.Main) {
                AlertDialog.Builder(activity)
                    .setTitle("Mise à jour disponible")
                    .setMessage("Une nouvelle version est disponible sur GitHub. Télécharger et installer ?")
                    .setPositiveButton("Installer") { _, _ ->
                        activity.lifecycleScope.launch {
                            downloadAndInstall(activity, apkUrl, onStatus)
                        }
                    }
                    .setNegativeButton("Plus tard", null)
                    .show()
            }
        } catch (e: Exception) {
            AppLogger.err("Update", "check failed", e)
        }
    }

    private suspend fun fetchLatestRelease(): JsonObject? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        val body: String = http.newCall(req).execute().use { it.body?.string() }
            ?: return@withContext null
        Gson().fromJson(body, JsonObject::class.java)
    }

    private fun findApkUrl(release: JsonObject): String? {
        val assets = release.getAsJsonArray("assets") ?: return null
        for (a in assets) {
            val obj = a.asJsonObject
            if (obj.get("name")?.asString?.endsWith(".apk") == true) {
                return obj.get("browser_download_url")?.asString
            }
        }
        return null
    }

    private suspend fun downloadAndInstall(
        activity: AppCompatActivity,
        apkUrl: String,
        onStatus: (String) -> Unit
    ) {
        try {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(activity)
                        .setTitle("Permission requise")
                        .setMessage("Autorise l'installation depuis cette source dans les paramètres.")
                        .setPositiveButton("Paramètres") { _, _ ->
                            activity.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${activity.packageName}")
                                )
                            )
                        }
                        .setNegativeButton("Annuler", null)
                        .show()
                }
                return
            }

            withContext(Dispatchers.Main) { onStatus("Téléchargement de la mise à jour...") }

            val apkFile: File = withContext(Dispatchers.IO) {
                val file = File(activity.cacheDir, "update.apk")
                val req = Request.Builder().url(apkUrl).build()
                http.newCall(req).execute().use { resp ->
                    val body = resp.body ?: throw RuntimeException("Corps de réponse vide")
                    val total: Long = body.contentLength()
                    var downloaded = 0L
                    FileOutputStream(file).use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(8192)
                            var n: Int
                            while (input.read(buf).also { n = it } != -1) {
                                out.write(buf, 0, n)
                                downloaded += n
                                if (total > 0) {
                                    val pct: Int = (downloaded * 100 / total).toInt()
                                    if (pct % 10 == 0) {
                                        activity.runOnUiThread { onStatus("Téléchargement $pct%...") }
                                    }
                                }
                            }
                        }
                    }
                }
                AppLogger.log("Update", "APK téléchargé → ${file.absolutePath}")
                file
            }

            withContext(Dispatchers.Main) {
                onStatus("Lancement de l'installation...")
                val uri = FileProvider.getUriForFile(
                    activity, "${activity.packageName}.provider", apkFile
                )
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        } catch (e: Exception) {
            AppLogger.err("Update", "download/install failed", e)
            withContext(Dispatchers.Main) { onStatus("⚠ Mise à jour échouée — voir Logs") }
        }
    }
}
