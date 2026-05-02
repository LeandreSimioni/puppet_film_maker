package eu.ueueue.muppet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Lit les fichiers de configuration depuis le repo GitHub au démarrage.
 * puppet_constraints.md et director_memory.md sont injectés dans les prompts.
 *
 * URL raw GitHub : https://raw.githubusercontent.com/{user}/{repo}/main/{fichier}
 * Modifier GITHUB_USER et GITHUB_REPO selon ton compte.
 */
object GitHubConfig {

    private const val GITHUB_USER = "leandresimioni"
    private const val GITHUB_REPO = "puppet_film_maker"
    private const val BRANCH = "main"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    var constraints: String = ""      // puppet_constraints.md
    var directorMemory: String = ""   // director_memory.md
    var orchestratorDoc: String = ""  // orchestrateur.md — actions disponibles
    var isLoaded: Boolean = false

    /**
     * À appeler au démarrage de l'app.
     * En cas d'échec réseau, utilise les valeurs embarquées dans les assets.
     */
    suspend fun load(fallbackAssets: android.content.res.AssetManager) {
        withContext(Dispatchers.IO) {
            constraints = fetchFile("puppet_constraints.md")
                ?: fallbackAssets.open("config/puppet_constraints.md").reader().readText()

            directorMemory = fetchFile("director_memory.md")
                ?: fallbackAssets.open("config/director_memory.md").reader().readText()

            orchestratorDoc = fetchFile("orchestrateur.md")
                ?: fallbackAssets.open("config/orchestrateur.md").reader().readText()

            isLoaded = true
        }
    }

    private fun fetchFile(filename: String): String? {
        return try {
            val url = "https://raw.githubusercontent.com/$GITHUB_USER/$GITHUB_REPO/$BRANCH/$filename"
            val request = Request.Builder().url(url).build()
            val response = http.newCall(request).execute()
            if (response.isSuccessful) response.body?.string() else null
        } catch (e: Exception) {
            null // fallback sur les assets
        }
    }
}
