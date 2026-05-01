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
        framesDir = File(activity.cacheDir, "frames_${System.currentTimeMillis()}").also { it.mkdirs() }
        expectedFrames = totalFrames
    }

    @JavascriptInterface
    fun onFrame(base64Data: String, frameIndex: Int) {
        val dir = framesDir ?: return
        val bytes = Base64.decode(base64Data.substringAfter("base64,"), Base64.DEFAULT)
        FileOutputStream(File(dir, "frame_%04d.jpg".format(frameIndex))).use { it.write(bytes) }
    }

    @JavascriptInterface
    fun onRenderComplete() {
        val dir = framesDir ?: return
        activity.runOnUiThread {
            activity.onRenderComplete(dir.absolutePath, expectedFrames)
        }
    }
}
