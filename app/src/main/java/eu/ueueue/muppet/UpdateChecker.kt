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
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun checkAndPrompt(activity: AppCompatActivity, onStatus: (String) -> Unit) {
        try {
            val remoteVersion: Int = fetchRemoteVersionCode() ?: run {
                AppLogger.log("Update", "version.json introuvable — skip")
                return
            }
            val localVersion: Int = BuildConfig.VERSION_CODE

            AppLogger.log("Update", "local=$localVersion remote=$remoteVersion")

            if (remoteVersion <= localVersion) {
                AppLogger.log("Update", "application à jour (build #$localVersion)")
                return
            }

            val apkUrl: String = fetchApkUrl() ?: run {
                AppLogger.err("Update", "aucun APK dans la release")
                return
            }

            AppLogger.log("Update", "nouvelle version build #$remoteVersion → $apkUrl")

            withContext(Dispatchers.Main) {
                AlertDialog.Builder(activity)
                    .setTitle("Mise à jour disponible")
                    .setMessage("Build #$remoteVersion disponible (installé : #$localVersion). Télécharger et installer ?")
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

    private suspend fun fetchRemoteVersionCode(): Int? = withContext(Dispatchers.IO) {
        val url = "https://github.com/${BuildConfig.GITHUB_REPO}/releases/latest/download/version.json"
        val req = Request.Builder().url(url).build()
        val body: String = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            resp.body?.string()
        } ?: return@withContext null
        Gson().fromJson(body, JsonObject::class.java)?.get("versionCode")?.asInt
    }

    private suspend fun fetchApkUrl(): String =
        "https://github.com/${BuildConfig.GITHUB_REPO}/releases/latest/download/muppet-debug.apk"

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
                    FileOutputStream(file).use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(8192)
                            var n: Int
                            while (input.read(buf).also { n = it } != -1) {
                                out.write(buf, 0, n)
                                downloaded += n
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
