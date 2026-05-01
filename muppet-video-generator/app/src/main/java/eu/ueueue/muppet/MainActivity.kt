package eu.ueueue.muppet

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import eu.ueueue.muppet.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Flow complet :
 *
 * 1. Saisie texte brut
 * 2. Rédacteur LLM → script avec didascalies [éditable]
 * 3. Valider → TTS → audio WAV
 * 4. STT → timestamps mot par mot
 * 5. Orchestrateur LLM → timeline JSON [éditable / raffinable]
 * 6. Rendu Three.js → frames JPEG → FFmpegKit → MP4
 * 7. Chat de correction → nouvelle timeline → nouveau rendu (audio réutilisé)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var puppetBridge: PuppetBridge
    private lateinit var videoExporter: VideoExporter
    private lateinit var mistralClient: MistralClient

    // État courant de la session
    private var currentScript: String = ""
    private var currentAudioPath: String? = null
    private var currentSttResult: SttResult? = null
    private var currentTimeline: JsonArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mistralClient = MistralClient(this)
        videoExporter = VideoExporter(this)

        setupWebView()
        setupButtons()

        // Charger les fichiers de config depuis GitHub au démarrage
        lifecycleScope.launch {
            binding.statusText.text = "Chargement de la configuration..."
            GitHubConfig.load(assets)
            binding.statusText.text = "Prêt."
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
        binding.webView.webViewClient = WebViewClient()
        binding.webView.loadUrl("file:///android_asset/puppet/index.html")
    }

    private fun setupButtons() {

        // ÉTAPE 1 : générer le script avec didascalies
        binding.btnRedact.setOnClickListener {
            val subject = binding.scriptInput.text.toString()
            if (subject.isBlank()) return@setOnClickListener
            lifecycleScope.launch {
                binding.statusText.text = "Rédaction du script..."
                val script = mistralClient.redact(subject)
                binding.scriptInput.setText(script)
                binding.statusText.text = "Script généré — modifie-le si besoin, puis valide."
            }
        }

        // ÉTAPE 2 : valider le script → TTS → STT → Orchestrateur
        binding.btnValidate.setOnClickListener {
            currentScript = binding.scriptInput.text.toString()
            if (currentScript.isBlank()) return@setOnClickListener
            lifecycleScope.launch {
                generateAudioAndOrchestrate()
            }
        }

        // ÉTAPE 3 : lancer le rendu avec la timeline courante
        binding.btnRender.setOnClickListener {
            val timeline = currentTimeline ?: return@setOnClickListener
            val stt = currentSttResult ?: return@setOnClickListener
            val audio = currentAudioPath ?: return@setOnClickListener
            lifecycleScope.launch {
                launchRender(timeline, stt, audio)
            }
        }

        // ÉTAPE 4 : corriger la timeline via chat
        binding.btnRefine.setOnClickListener {
            val request = binding.refineInput.text.toString()
            val timeline = currentTimeline ?: return@setOnClickListener
            val stt = currentSttResult ?: return@setOnClickListener
            if (request.isBlank()) return@setOnClickListener
            lifecycleScope.launch {
                binding.statusText.text = "Correction de la timeline..."
                val newTimeline = mistralClient.refineOrchestration(timeline, request, stt)
                currentTimeline = newTimeline
                binding.statusText.text = "Timeline corrigée — relance le rendu."
            }
        }

        // Test Phase 1 — export sans audio
        binding.btnTestExport.setOnClickListener {
            binding.statusText.text = "Test export 5s..."
            binding.webView.evaluateJavascript("startRender(150, 30)", null)
        }
    }

    private suspend fun generateAudioAndOrchestrate() {
        // TTS : texte seul (sans les didascalies)
        binding.statusText.text = "Synthèse vocale..."
        val textOnly = extractTextOnly(currentScript)
        currentAudioPath = mistralClient.tts(textOnly)

        // STT : timestamps précis
        binding.statusText.text = "Analyse des timestamps..."
        currentSttResult = mistralClient.stt(currentAudioPath!!)

        // Orchestrateur : script complet + timestamps → timeline
        binding.statusText.text = "Orchestration..."
        currentTimeline = mistralClient.orchestrate(currentScript, currentSttResult!!)

        binding.statusText.text = "Timeline prête — lance le rendu ou demande des corrections."
        binding.btnRender.isEnabled = true
        binding.btnRefine.isEnabled = true
    }

    private suspend fun launchRender(timeline: JsonArray, stt: SttResult, audioPath: String) {
        binding.statusText.text = "Rendu en cours..."
        val srtPath = videoExporter.generateSrt(stt)
        val timelineJson = timeline.toString()
        binding.webView.evaluateJavascript(
            "startRenderWithTimeline($timelineJson, ${stt.durationSeconds})", null
        )
        // La suite est gérée par onRenderComplete via PuppetBridge
        puppetBridge.pendingAudioPath = audioPath
        puppetBridge.pendingSrtPath = srtPath
    }

    fun onRenderComplete(framesDir: String, frameCount: Int) {
        binding.statusText.text = "Assemblage vidéo..."
        lifecycleScope.launch {
            val outputPath = videoExporter.assembleVideo(
                framesDir = framesDir,
                audioPath = puppetBridge.pendingAudioPath,
                srtPath = puppetBridge.pendingSrtPath,
                fps = 30,
                outputName = "muppet_${System.currentTimeMillis()}.mp4"
            )
            binding.statusText.text = "✓ Vidéo exportée dans ta galerie"
        }
    }

    /**
     * Extrait uniquement le texte parlé — retire les didascalies entre crochets.
     */
    private fun extractTextOnly(script: String): String {
        return script.replace(Regex("\\[.*?\\]"), "").trim()
    }
}
