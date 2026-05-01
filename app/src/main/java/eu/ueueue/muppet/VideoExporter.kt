package eu.ueueue.muppet

import android.content.Context
import android.os.Environment
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VideoExporter(private val context: Context) {

    suspend fun assembleVideo(
        framesDir: String,
        audioPath: String?,
        srtPath: String?,
        fps: Int = 30,
        outputName: String = "muppet_${System.currentTimeMillis()}.mp4"
    ): String = withContext(Dispatchers.IO) {
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val outputFile = File(outputDir, outputName)
        val vf = buildString {
            if (srtPath != null) append("subtitles='$srtPath',")
            append("scale=1080:1080")
        }
        val cmd = buildString {
            append("-framerate $fps -i '$framesDir/frame_%04d.jpg' ")
            if (audioPath != null) append("-i '$audioPath' ")
            append("-vf '$vf' -c:v libx264 -preset fast -crf 23 ")
            if (audioPath != null) append("-c:a aac -shortest ")
            append("-y '${outputFile.absolutePath}'")
        }
        val session = FFmpegKit.execute(cmd)
        if (!ReturnCode.isSuccess(session.returnCode))
            throw RuntimeException("FFmpeg failed: ${session.allLogsAsString}")
        outputFile.absolutePath
    }

    fun generateSrt(timestamps: SttResult): String {
        val srtFile = File(context.cacheDir, "subs_${System.currentTimeMillis()}.srt")
        val sb = StringBuilder()
        timestamps.words.forEachIndexed { i, word ->
            sb.appendLine(i + 1)
            sb.appendLine("${fmt(word.start)} --> ${fmt(word.end)}")
            sb.appendLine(word.text)
            sb.appendLine()
        }
        srtFile.writeText(sb.toString())
        return srtFile.absolutePath
    }

    private fun fmt(s: Double): String {
        val h = (s / 3600).toInt(); val m = ((s % 3600) / 60).toInt()
        val sec = (s % 60).toInt(); val ms = ((s % 1) * 1000).toInt()
        return "%02d:%02d:%02d,%03d".format(h, m, sec, ms)
    }
}
