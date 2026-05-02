package eu.ueueue.muppet

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import android.util.Base64

data class WordTimestamp(val text: String, val start: Double, val end: Double)
data class SttResult(val words: List<WordTimestamp>, val durationSeconds: Double)
data class VoiceItem(val id: String, val name: String)

class MistralClient(private val context: Context) {

    private val apiKey: String by lazy { loadApiKey() }
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private var cachedMarieVoiceId: String? = null

    private fun loadApiKey(): String {
        val prefs = context.getSharedPreferences("muppet_prefs", Context.MODE_PRIVATE)
        prefs.getString("mistral_api_key", null)?.takeIf { it.isNotBlank() }?.let { return it }
        // Migration depuis config.json si présent (puis suppression non nécessaire, gitignored)
        return try {
            context.assets.open("config/config.json").use {
                gson.fromJson(it.reader(), JsonObject::class.java)
                    .get("mistral_api_key").asString.also { key ->
                        prefs.edit().putString("mistral_api_key", key).apply()
                    }
            }
        } catch (e: Exception) {
            throw RuntimeException("Clé API Mistral manquante — configure-la dans l'app.")
        }
    }

    // ─────────────────────────────────────────
    // ÉTAPE 1 : Rédacteur — texte brut → script avec didascalies
    // ─────────────────────────────────────────
    suspend fun redact(subject: String): String = withContext(Dispatchers.IO) {
        val systemPrompt = """
Tu es auteur de théâtre de marionnettes. Écris un script en alternant :
- Texte nu = parole prononcée par la marionnette
- [Didascalie] = mouvement entre crochets
- [Pause Xs] = silence de X secondes

CONTRAINTES PHYSIQUES ABSOLUES — la marionnette ne peut faire QUE :
- Tourner/incliner la tête (gauche, droite, haut, bas)
- Diriger le regard (vers la caméra, vers le bas, vers la gauche/droite)
- Exprimer une émotion sur le visage (sourire, tristesse, colère, surprise)
- Changer le ton de la voix [Angry], [Sad], [Happy], [Excited], [Neutral]
- Faire une pause [Pause Xs]
- Faire des gestes avec les bras (lever un bras, geste expressif, pointer, hausser les épaules)

INTERDIT (physiquement impossible) :
- Entrer ou sortir de scène
- Se déplacer, marcher, s'approcher
- Tenir ou saisir des objets

Exemples de didascalies valides :
[Elle regarde vers le bas], [Pause 1.5s], [Angry], [Regard caméra], [Incline la tête], [Sad],
[Regard vers la gauche], [Lève les bras], [Geste expressif], [Hausse les épaules], [Pointe du doigt]
        """.trimIndent()

        callLLM(systemPrompt, subject)
    }

    // ─────────────────────────────────────────
    // VOIX — liste toutes les voix disponibles
    // ─────────────────────────────────────────
    suspend fun listVoices(): List<VoiceItem> = withContext(Dispatchers.IO) {
        val voices = mutableListOf<VoiceItem>()
        var offset = 0
        val limit = 20
        while (true) {
            val request = Request.Builder()
                .url("https://api.mistral.ai/v1/audio/voices?limit=$limit&offset=$offset")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val response = http.newCall(request).execute()
            if (!response.isSuccessful) throw RuntimeException("Voices error ${response.code}: ${response.body?.string()}")
            val json = gson.fromJson(response.body!!.string(), JsonObject::class.java)
            val items = json.getAsJsonArray("items")
            items.forEach { el ->
                val v = el.asJsonObject
                voices += VoiceItem(v.get("id").asString, v.get("name").asString)
            }
            val total = json.get("total").asInt
            offset += items.size()
            if (offset >= total) break
        }
        AppLogger.log("Voices", "disponibles : ${voices.map { "${it.name}=${it.id}" }}")
        voices
    }

    private suspend fun resolveMarieVoiceId(emotion: String = "Excited"): String {
        cachedMarieVoiceId?.let { return it }
        val voices = listVoices()
        val marie = voices.firstOrNull { it.name.equals("marie - $emotion", ignoreCase = true) }
            ?: voices.firstOrNull { it.name.startsWith("marie", ignoreCase = true) }
            ?: throw RuntimeException("Voix 'Marie' introuvable — vérifiez votre compte Mistral.")
        cachedMarieVoiceId = marie.id
        AppLogger.log("Voices", "Marie sélectionnée : ${marie.name} (${marie.id})")
        return marie.id
    }

    // ─────────────────────────────────────────
    // ÉTAPE 2 : TTS — texte brut → audio
    // Simple et sans logique de timing. L'orchestrateur a le mot final.
    // ─────────────────────────────────────────
    suspend fun tts(text: String): String =
        withContext(Dispatchers.IO) {
            val marieId = resolveMarieVoiceId()
            val body = JsonObject().apply {
                addProperty("model", "voxtral-mini-tts-2603")
                addProperty("input", text)
                addProperty("voice_id", marieId)
                addProperty("response_format", "mp3")
            }
            AppLogger.log("TTS", "request body: $body")
            val request = Request.Builder()
                .url("https://api.mistral.ai/v1/audio/speech")
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = http.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "(vide)"
                throw RuntimeException("TTS error ${response.code}: $errorBody")
            }
            val responseBody = response.body!!.string()
            val audioBytes = try {
                val json = gson.fromJson(responseBody, JsonObject::class.java)
                Base64.decode(json.get("audio_data").asString, Base64.DEFAULT)
            } catch (e: Exception) {
                responseBody.toByteArray(Charsets.ISO_8859_1)
            }
            val audioFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
            audioFile.writeBytes(audioBytes)
            audioFile.absolutePath
        }

    // ─────────────────────────────────────────
    // ÉTAPE 3 : STT — audio → timestamps mot par mot
    // ─────────────────────────────────────────
    suspend fun stt(audioPath: String): SttResult = withContext(Dispatchers.IO) {
        val audioFile = File(audioPath)
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/mpeg".toMediaType()))
            .addFormDataPart("model", "voxtral-mini-latest")
            .addFormDataPart("timestamp_granularities[]", "word")
            .build()
        val request = Request.Builder()
            .url("https://api.mistral.ai/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(multipart)
            .build()
        val response = http.newCall(request).execute()
        val responseBody = response.body!!.string()
        if (!response.isSuccessful) throw RuntimeException("STT error ${response.code}: $responseBody")
        AppLogger.log("STT", "response: $responseBody")
        val json = gson.fromJson(responseBody, JsonObject::class.java)

        // Priorité : words au niveau racine (timestamp_granularities=word)
        val topWords = json.getAsJsonArray("words")
        if (topWords != null && topWords.size() > 0) {
            val words = topWords.map {
                val w = it.asJsonObject
                WordTimestamp(w.get("word").asString, w.get("start").asDouble, w.get("end").asDouble)
            }
            return@withContext SttResult(words, words.last().end)
        }

        // Fallback : segments (un segment = une unité de lip sync)
        val segments = json.getAsJsonArray("segments")
            ?: throw RuntimeException("STT: ni 'words' ni 'segments'. Réponse : $responseBody")
        val words = segments.map {
            val s = it.asJsonObject
            WordTimestamp(s.get("text").asString, s.get("start").asDouble, s.get("end").asDouble)
        }
        AppLogger.log("STT", "fallback segments : ${words.size} segments, durée=${words.lastOrNull()?.end}s")
        SttResult(words, words.lastOrNull()?.end ?: 0.0)
    }

    // ─────────────────────────────────────────
    // ÉTAPE 4 : Orchestrateur — script + timestamps → timeline JSON
    // Reçoit les contraintes et la mémoire du metteur en scène depuis GitHub
    // ─────────────────────────────────────────
    suspend fun orchestrate(script: String, sttResult: SttResult): JsonArray =
        withContext(Dispatchers.IO) {
            val systemPrompt = """
Tu es le RÉGISSEUR FINAL. Le script et l'audio brut sont ta matière première — tu as le mot final sur tout.
Tu génères une timeline JSON qui contrôle absolument tout : animation, audio, fond.

FORMAT : [{"t": 0.0, "action": "...", ...}, ...]
Les "t" sont en secondes depuis le début de l'audio original.
Réponds UNIQUEMENT avec le JSON, sans commentaire.

PHILOSOPHIE :
- Tu es libre de restructurer le temps. Si le script dit [Pause 2s], insère un insertSilence(2.0) au bon moment.
- "Ne rien faire" pendant un intervalle = puppet figé dans son dernier état — c'est intentionnel et expressif.
- Tu n'es pas lié au rythme de l'audio brut. Tu peux ralentir, respirer, dramatiser.

=== RÉFÉRENCE DES ACTIONS DISPONIBLES ===
${GitHubConfig.orchestratorDoc}

=== CONTRAINTES PHYSIQUES ===
${GitHubConfig.constraints}

=== MÉMOIRE DU METTEUR EN SCÈNE ===
${GitHubConfig.directorMemory}
            """.trimIndent()

            val userContent = buildString {
                appendLine("SCRIPT:"); appendLine(script); appendLine()
                appendLine("TIMESTAMPS:")
                sttResult.words.forEach { appendLine("  ${it.start}s–${it.end}s : ${it.text}") }
                appendLine("Durée totale : ${sttResult.durationSeconds}s")
            }

            val raw = callLLM(systemPrompt, userContent)
            gson.fromJson(raw.trim().removePrefix("```json").removeSuffix("```").trim(), JsonArray::class.java)
        }

    // ─────────────────────────────────────────
    // BOUCLE DE CORRECTION — chat avec l'Orchestrateur
    // L'audio n'est PAS refait — seulement la timeline
    // ─────────────────────────────────────────
    suspend fun refineOrchestration(
        currentTimeline: JsonArray,
        userRequest: String,
        sttResult: SttResult
    ): JsonArray = withContext(Dispatchers.IO) {

        val systemPrompt = """
Tu es régisseur de marionnettes. On te donne une timeline existante et une demande de modification.
Retourne la timeline corrigée en JSON, même format qu'avant.
Réponds UNIQUEMENT avec le JSON.

=== RÉFÉRENCE DES ACTIONS DISPONIBLES ===
${GitHubConfig.orchestratorDoc}

=== CONTRAINTES PHYSIQUES ===
${GitHubConfig.constraints}

=== MÉMOIRE DU METTEUR EN SCÈNE ===
${GitHubConfig.directorMemory}
        """.trimIndent()

        val userContent = """
TIMELINE ACTUELLE :
$currentTimeline

DEMANDE DE MODIFICATION :
$userRequest

DURÉE AUDIO : ${sttResult.durationSeconds}s
        """.trimIndent()

        val raw = callLLM(systemPrompt, userContent)
        gson.fromJson(raw.trim().removePrefix("```json").removeSuffix("```").trim(), JsonArray::class.java)
    }

    // ─────────────────────────────────────────
    // Utilitaire LLM
    // ─────────────────────────────────────────
    private fun callLLM(systemPrompt: String, userContent: String): String {
        val body = JsonObject().apply {
            addProperty("model", "mistral-medium-latest")
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userContent)
            )))
            addProperty("temperature", 0.3)
        }
        val request = Request.Builder()
            .url("https://api.mistral.ai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val response = http.newCall(request).execute()
        if (!response.isSuccessful) throw RuntimeException("LLM error: ${response.code}")
        val json = gson.fromJson(response.body!!.string(), JsonObject::class.java)
        return json.getAsJsonArray("choices")[0].asJsonObject
            .getAsJsonObject("message").get("content").asString
    }
}
