package eu.ueueue.muppet

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import eu.ueueue.muppet.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var puppetBridge: PuppetBridge
    private lateinit var videoExporter: VideoExporter
    private lateinit var mistralClient: MistralClient

    private var currentScript: String = ""
    private var currentAudioPath: String? = null
    private var currentSttResult: SttResult? = null
    private var currentTimeline: JsonArray? = null

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val pickBackground = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val dest = File(filesDir, "background.jpg")
            contentResolver.openInputStream(uri)!!.use { it.copyTo(dest.outputStream()) }
            getSharedPreferences("muppet_prefs", Context.MODE_PRIVATE)
                .edit().putString("background_path", dest.absolutePath).apply()
            withContext(Dispatchers.Main) {
                injectBackground(dest.absolutePath)
                setStatus("Fond mis à jour.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.init(this)
        installCrashHandler()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mistralClient = MistralClient(this)
        videoExporter = VideoExporter(this)

        setupWebView()
        setupButtons()
        checkApiKey()
        binding.scriptInput.setOnTouchListener { v, _ -> v.parent.requestDisallowInterceptTouchEvent(true); false }

        lifecycleScope.launch {
            setStatus("Vérification des mises à jour...")
            UpdateChecker.checkAndPrompt(this@MainActivity) { setStatus(it) }

            setStatus("Chargement de la configuration...")
            try {
                GitHubConfig.load(assets)
                setStatus("Prêt.")
            } catch (e: Exception) {
                AppLogger.err("Config", "load failed", e)
                setStatus("Config GitHub indisponible — fallback local.")
            }
        }
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            AppLogger.err("CRASH", "thread=${thread.name}", ex)
            defaultHandler?.uncaughtException(thread, ex)
        }
    }

    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
        }
        puppetBridge = PuppetBridge(this, videoExporter)
        binding.webView.addJavascriptInterface(puppetBridge, "Android")
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                AppLogger.log("WebView", "page chargée : $url")
                val bgPath = getSharedPreferences("muppet_prefs", Context.MODE_PRIVATE)
                    .getString("background_path", null)
                if (bgPath != null && File(bgPath).exists()) injectBackground(bgPath)
            }
        }
        binding.webView.loadUrl("file:///android_asset/puppet/index.html")
    }

    private fun setupButtons() {

        binding.btnRedact.setOnClickListener {
            val subject = binding.scriptInput.text.toString()
            if (subject.isBlank()) return@setOnClickListener
            lifecycleScope.launch {
                try {
                    setStatus("Rédaction du script...")
                    val script = mistralClient.redact(subject)
                    binding.scriptInput.setText(script)
                    setStatus("Script généré — modifie-le si besoin, puis valide.")
                } catch (e: Exception) {
                    showError("Erreur rédaction", e)
                }
            }
        }

        binding.btnValidate.setOnClickListener {
            currentScript = binding.scriptInput.text.toString()
            if (currentScript.isBlank()) return@setOnClickListener
            lifecycleScope.launch {
                try {
                    generateAudioAndOrchestrate()
                } catch (e: Exception) {
                    showError("Erreur pipeline audio", e)
                }
            }
        }

        binding.btnRender.setOnClickListener {
            val stt = currentSttResult ?: return@setOnClickListener
            val audio = currentAudioPath ?: return@setOnClickListener
            val timelineJson = binding.timelineInput.text.toString().trim()
            val timeline = try {
                com.google.gson.JsonParser.parseString(timelineJson).asJsonArray
            } catch (e: Exception) {
                showError("JSON timeline invalide", e); return@setOnClickListener
            }
            currentTimeline = timeline
            lifecycleScope.launch {
                try {
                    launchRender(timeline, stt, audio)
                } catch (e: Exception) {
                    showError("Erreur rendu", e)
                }
            }
        }

        binding.btnRefine.setOnClickListener {
            val request = binding.refineInput.text.toString()
            val timeline = currentTimeline ?: return@setOnClickListener
            val stt = currentSttResult ?: return@setOnClickListener
            if (request.isBlank()) return@setOnClickListener
            lifecycleScope.launch {
                try {
                    setStatus("Correction de la timeline...")
                    val newTimeline = mistralClient.refineOrchestration(timeline, request, stt)
                    currentTimeline = newTimeline
                    binding.timelineInput.setText(gson.toJson(newTimeline))
                    setStatus("Timeline corrigée — vérifie le JSON, puis relance le rendu.")
                } catch (e: Exception) {
                    showError("Erreur correction", e)
                }
            }
        }

        binding.btnTestExport.setOnClickListener {
            AppLogger.log("UI", "btnTestExport cliqué")
            setStatus("Test export 5s — démarrage rendu JS...")
            binding.webView.evaluateJavascript("startRender(150, 30)") { result ->
                AppLogger.log("WebView", "startRender retour JS : $result")
            }
        }

        binding.btnBackground.setOnClickListener { pickBackground.launch("image/*") }

        binding.btnLogs.setOnClickListener { showLogsDialog() }

        binding.btnApiKey.setOnClickListener { promptApiKey() }
    }

    fun setStatus(msg: String) {
        AppLogger.log("Status", msg)
        binding.statusText.text = msg
    }

    fun showError(context: String, e: Throwable? = null) {
        val msg = if (e != null) "$context : ${e.message}" else context
        AppLogger.err("UI", msg, e)
        binding.statusText.text = "⚠ $msg — voir Logs"
    }

    private fun showLogsDialog() {
        val tv = TextView(this).apply {
            text = AppLogger.read()
            setPadding(32, 24, 32, 24)
            setTextColor(0xFFE8E0D0.toInt())
            setBackgroundColor(0xFF0A0400.toInt())
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val scroll = ScrollView(this).apply {
            addView(tv)
            post { fullScroll(ScrollView.FOCUS_DOWN) }
        }
        AlertDialog.Builder(this)
            .setTitle("Logs")
            .setView(scroll)
            .setPositiveButton("Fermer", null)
            .setNeutralButton("Copier") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("logs", AppLogger.read()))
                setStatus("Logs copiés dans le presse-papier.")
            }
            .show()
    }

    private fun checkApiKey() {
        val prefs = getSharedPreferences("muppet_prefs", Context.MODE_PRIVATE)
        if (prefs.getString("mistral_api_key", null).isNullOrBlank()) {
            try {
                assets.open("config/config.json").use {
                    val key = com.google.gson.Gson()
                        .fromJson(it.reader(), com.google.gson.JsonObject::class.java)
                        .get("mistral_api_key")?.asString
                    if (!key.isNullOrBlank()) {
                        prefs.edit().putString("mistral_api_key", key).apply()
                        return
                    }
                }
            } catch (e: Exception) { /* pas de config.json */ }
            promptApiKey()
        }
    }

    fun promptApiKey(onSaved: (() -> Unit)? = null) {
        val input = EditText(this).apply {
            hint = "sk-..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Clé API Mistral")
            .setMessage("Stockée uniquement sur cet appareil — jamais dans le code ni le repo.")
            .setView(input)
            .setPositiveButton("Enregistrer") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotBlank()) {
                    getSharedPreferences("muppet_prefs", Context.MODE_PRIVATE)
                        .edit().putString("mistral_api_key", key).apply()
                    setStatus("Clé API sauvegardée.")
                    onSaved?.invoke()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private suspend fun generateAudioAndOrchestrate() {
        setStatus("Synthèse vocale...")
        val textOnly = extractTextOnly(currentScript)
        currentAudioPath = mistralClient.tts(textOnly)

        setStatus("Analyse des timestamps...")
        currentSttResult = mistralClient.stt(currentAudioPath!!)

        setStatus("Orchestration...")
        currentTimeline = mistralClient.orchestrate(currentScript, currentSttResult!!)

        setStatus("Timeline prête — modifie le JSON si besoin, puis lance le rendu.")
        binding.labelTimeline.visibility = View.VISIBLE
        binding.timelineInput.visibility = View.VISIBLE
        binding.timelineInput.setText(gson.toJson(currentTimeline))
        binding.btnRender.isEnabled = true
        binding.btnRefine.isEnabled = true
    }

    private suspend fun launchRender(timeline: JsonArray, stt: SttResult, audioPath: String) {
        setStatus("Rendu en cours...")
        val srtPath = videoExporter.generateSrt(stt)
        val timelineJson = timeline.toString()
        val wordsJson = stt.words.joinToString(",", "[", "]") {
            """{"start":${it.start},"end":${it.end}}"""
        }
        binding.webView.evaluateJavascript(
            "startRenderWithTimeline($timelineJson, ${stt.durationSeconds}, $wordsJson)", null
        )
        puppetBridge.pendingAudioPath = audioPath
        puppetBridge.pendingSrtPath = srtPath
    }

    fun onRenderComplete(framesDir: String, frameCount: Int) {
        setStatus("Assemblage vidéo ($frameCount frames)...")
        lifecycleScope.launch {
            try {
                val outputPath = videoExporter.assembleVideo(
                    framesDir = framesDir,
                    audioPath = puppetBridge.pendingAudioPath,
                    srtPath = puppetBridge.pendingSrtPath,
                    fps = 30,
                    outputName = "muppet_${System.currentTimeMillis()}.mp4"
                )
                setStatus("✓ Vidéo dans Téléchargements : $outputPath")
            } catch (e: Exception) {
                showError("Assemblage FFmpeg", e)
            }
        }
    }

    private fun injectBackground(path: String) {
        binding.webView.evaluateJavascript("setBackgroundUrl('file://$path')", null)
    }

    private fun extractTextOnly(script: String): String =
        script.replace(Regex("\\[.*?\\]"), "").trim()
}
