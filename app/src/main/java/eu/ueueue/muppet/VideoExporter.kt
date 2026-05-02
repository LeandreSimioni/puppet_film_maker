package eu.ueueue.muppet

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
        // FFmpeg écrit d'abord dans le cache (toujours accessible en écriture)
        val tempFile = File(context.cacheDir, outputName)

        AppLogger.log("FFmpeg", "framesDir=$framesDir audio=$audioPath srt=$srtPath")
        AppLogger.log("FFmpeg", "temp=${tempFile.absolutePath}")

        val frameCount = File(framesDir).listFiles()?.size ?: 0
        AppLogger.log("FFmpeg", "frames trouvés : $frameCount")
        if (frameCount == 0) throw RuntimeException("Aucun frame JPEG dans $framesDir")

        val cmd = buildString {
            append("-framerate $fps -i '$framesDir/frame_%04d.jpg' ")
            if (audioPath != null) append("-i '$audioPath' ")
            if (srtPath != null) append("-i '$srtPath' ")
            append("-map 0:v ")
            if (audioPath != null) append("-map 1:a ")
            if (srtPath != null) {
                val srtIdx = if (audioPath != null) 2 else 1
                append("-map $srtIdx:s ")
            }
            append("-vf 'scale=1080:1080' ")
            append("-c:v h264_mediacodec -b:v 6M ")
            if (audioPath != null) append("-c:a aac -ar 44100 -shortest ")
            if (srtPath != null) append("-c:s mov_text ")
            append("-y '${tempFile.absolutePath}'")
        }
        AppLogger.log("FFmpeg", "cmd: $cmd")

        val session = FFmpegKit.execute(cmd)
        val logs = session.allLogsAsString ?: ""
        AppLogger.log("FFmpeg", "rc=${session.returnCode} logs=${logs.takeLast(500)}")

        if (!ReturnCode.isSuccess(session.returnCode))
            throw RuntimeException("FFmpeg a échoué (rc=${session.returnCode})\n${logs.takeLast(300)}")

        // Copie dans Téléchargements (visible dans l'app Fichiers)
        val finalPath = copyToDownloads(tempFile, outputName)
        tempFile.delete()

        AppLogger.log("FFmpeg", "succès → $finalPath")
        finalPath
    }

    private fun copyToDownloads(src: File, name: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: throw RuntimeException("Impossible de créer l'entrée MediaStore")
            context.contentResolver.openOutputStream(uri)!!.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
            // Retourne un chemin lisible pour l'affichage
            "${Environment.DIRECTORY_DOWNLOADS}/$name"
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val dest = File(dir, name)
            src.copyTo(dest, overwrite = true)
            dest.absolutePath
        }
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
