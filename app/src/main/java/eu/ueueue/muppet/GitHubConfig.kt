package eu.ueueue.muppet

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// Lit actions-catalog.json depuis GitHub au démarrage, expose les sections au prompt orchestrateur.
// Fallback automatique sur les assets locaux en cas d'absence de réseau.
// URL GitHub : https://raw.githubusercontent.com/{GITHUB_USER}/{GITHUB_REPO}/main/app/src/main/assets/puppet/actions-catalog.json
object GitHubConfig {

    private const val GITHUB_USER = "leandresimioni"
    private const val GITHUB_REPO = "puppet_film_maker"
    private const val BRANCH      = "main"
    private const val CATALOG_PATH = "app/src/main/assets/puppet/actions-catalog.json"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    var constraints: String      = ""
    var directorMemory: String   = ""
    var orchestratorRules: String = ""
    var isLoaded: Boolean        = false

    suspend fun load(fallbackAssets: android.content.res.AssetManager) {
        withContext(Dispatchers.IO) {
            val raw = fetchFile(CATALOG_PATH)
                ?: fallbackAssets.open("puppet/actions-catalog.json").reader().readText()

            val json = gson.fromJson(raw, JsonObject::class.java)
            constraints       = formatConstraints(json)
            directorMemory    = formatDirector(json)
            orchestratorRules = formatRules(json)
            isLoaded = true
        }
    }

    private fun formatConstraints(json: JsonObject): String {
        val c = json.getAsJsonObject("constraints") ?: return ""
        return buildString {
            appendLine("## Contraintes physiques")
            c.entrySet().filter { it.key != "_doc" }.forEach { (k, v) ->
                appendLine("- $k : ${v.asString}")
            }
        }
    }

    private fun formatDirector(json: JsonObject): String {
        val d = json.getAsJsonObject("director") ?: return ""
        return buildString {
            appendLine("## Préférences de mise en scène")
            d.getAsJsonArray("preferences")?.forEach { appendLine("- ${it.asString}") }
            val worked = d.getAsJsonArray("what_worked")
            if (worked != null && worked.size() > 0) {
                appendLine("\n### Ce qui a bien fonctionné")
                worked.forEach { appendLine("- ${it.asString}") }
            }
            val didnt = d.getAsJsonArray("what_didnt_work")
            if (didnt != null && didnt.size() > 0) {
                appendLine("\n### Ce qui n'a pas fonctionné")
                didnt.forEach { appendLine("- ${it.asString}") }
            }
        }
    }

    private fun formatRules(json: JsonObject): String {
        val r = json.getAsJsonObject("orchestration_rules") ?: return ""
        return buildString {
            r.getAsJsonArray("rules")?.forEachIndexed { i, rule ->
                appendLine("${i + 1}. ${rule.asString}")
            }
        }
    }

    private fun fetchFile(path: String): String? {
        return try {
            val url = "https://raw.githubusercontent.com/$GITHUB_USER/$GITHUB_REPO/$BRANCH/$path"
            val response = http.newCall(Request.Builder().url(url).build()).execute()
            if (response.isSuccessful) response.body?.string() else null
        } catch (e: Exception) {
            null
        }
    }
}
