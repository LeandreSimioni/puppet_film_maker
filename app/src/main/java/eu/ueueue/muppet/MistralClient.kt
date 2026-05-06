package eu.ueueue.muppet

import android.content.Context
import com.google.gson.Gson
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

    // TTS — texte brut → audio MP3
    suspend fun tts(text: String): String = withContext(Dispatchers.IO) {
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

    // STT — audio → timestamps mot par mot
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

        val topWords = json.getAsJsonArray("words")
        if (topWords != null && topWords.size() > 0) {
            val words = topWords.map {
                val w = it.asJsonObject
                WordTimestamp(w.get("word").asString, w.get("start").asDouble, w.get("end").asDouble)
            }
            return@withContext SttResult(words, words.last().end)
        }

        // Fallback : segments
        val segments = json.getAsJsonArray("segments")
            ?: throw RuntimeException("STT: ni 'words' ni 'segments'. Réponse : $responseBody")
        val words = segments.map {
            val s = it.asJsonObject
            WordTimestamp(s.get("text").asString, s.get("start").asDouble, s.get("end").asDouble)
        }
        AppLogger.log("STT", "fallback segments : ${words.size} segments, durée=${words.lastOrNull()?.end}s")
        SttResult(words, words.lastOrNull()?.end ?: 0.0)
    }

    private suspend fun listVoices(): List<VoiceItem> = withContext(Dispatchers.IO) {
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

    private suspend fun resolveMarieVoiceId(): String {
        cachedMarieVoiceId?.let { return it }
        val voices = listVoices()
        val marie = voices.firstOrNull { it.name.startsWith("marie", ignoreCase = true) }
            ?: throw RuntimeException("Voix 'Marie' introuvable — vérifiez votre compte Mistral.")
        cachedMarieVoiceId = marie.id
        AppLogger.log("Voices", "Marie sélectionnée : ${marie.name} (${marie.id})")
        return marie.id
    }
}
