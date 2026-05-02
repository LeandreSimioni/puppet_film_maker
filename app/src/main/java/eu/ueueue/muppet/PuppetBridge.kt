package eu.ueueue.muppet

import android.util.Base64
import android.webkit.JavascriptInterface
import java.io.File
import java.io.FileOutputStream

class PuppetBridge(
    private val activity: MainActivity,
    private val videoExporter: VideoExporter
) {
    var pendingAudioPath: String? = null
    var pendingSrtPath: String? = null

    private var framesDir: File? = null
    private var expectedFrames = 0

    @JavascriptInterface
    fun onRenderStart(totalFrames: Int) {
        try {
            AppLogger.log("Bridge", "onRenderStart totalFrames=$totalFrames")
            framesDir = File(activity.cacheDir, "frames_${System.currentTimeMillis()}").also { it.mkdirs() }
            expectedFrames = totalFrames
            activity.runOnUiThread { activity.setStatus("Rendu : 0 / $totalFrames frames...") }
        } catch (e: Exception) {
            AppLogger.err("Bridge", "onRenderStart", e)
            activity.runOnUiThread { activity.showError("Erreur démarrage rendu", e) }
        }
    }

    @JavascriptInterface
    fun onFrame(base64Data: String, frameIndex: Int) {
        try {
            val dir = framesDir ?: return
            val payload = if (base64Data.contains("base64,")) base64Data.substringAfter("base64,") else base64Data
            val bytes = Base64.decode(payload, Base64.DEFAULT)
            FileOutputStream(File(dir, "frame_%04d.jpg".format(frameIndex))).use { it.write(bytes) }
            if (frameIndex % 30 == 0) {
                AppLogger.log("Bridge", "frame $frameIndex / $expectedFrames")
                activity.runOnUiThread { activity.setStatus("Rendu : $frameIndex / $expectedFrames frames...") }
            }
        } catch (e: Exception) {
            AppLogger.err("Bridge", "onFrame $frameIndex", e)
            activity.runOnUiThread { activity.showError("Erreur frame $frameIndex", e) }
        }
    }

    @JavascriptInterface
    fun onRenderComplete() {
        try {
            AppLogger.log("Bridge", "onRenderComplete dir=${framesDir?.absolutePath}")
            val dir = framesDir ?: run {
                AppLogger.err("Bridge", "onRenderComplete: framesDir est null")
                return
            }
            activity.runOnUiThread { activity.onRenderComplete(dir.absolutePath, expectedFrames) }
        } catch (e: Exception) {
            AppLogger.err("Bridge", "onRenderComplete", e)
            activity.runOnUiThread { activity.showError("Erreur fin rendu", e) }
        }
    }
}
