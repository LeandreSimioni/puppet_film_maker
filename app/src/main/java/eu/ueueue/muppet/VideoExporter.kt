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
        val frameCount = File(framesDir).listFiles()?.size ?: 0
        AppLogger.log("FFmpeg", "frames trouvés : $frameCount")
        if (frameCount == 0) throw RuntimeException("Aucun frame JPEG dans $framesDir")

        // Passe 1 : vidéo seule (h264_mediacodec ne mixe pas l'audio)
        val tempVideo = File(context.cacheDir, "vid_${System.currentTimeMillis()}.mp4")
        val cmd1 = "-framerate $fps -i '$framesDir/frame_%04d.jpg' " +
                   "-vf 'scale=1080:1080' -c:v h264_mediacodec -b:v 6M " +
                   "-y '${tempVideo.absolutePath}'"
        AppLogger.log("FFmpeg", "passe 1 (vidéo): $cmd1")
        runFFmpeg(cmd1)

        // Passe 2 : mux vidéo + audio (+ sous-titres optionnels) avec copy vidéo
        val tempFinal = File(context.cacheDir, outputName)
        val cmd2 = buildString {
            append("-i '${tempVideo.absolutePath}' ")
            if (audioPath != null) append("-i '$audioPath' ")
            append("-map 0:v ")
            if (audioPath != null) append("-map 1:a ")
            append("-c:v copy ")
            if (audioPath != null) append("-c:a aac -ar 44100 -shortest ")
            append("-y '${tempFinal.absolutePath}'")
        }
        AppLogger.log("FFmpeg", "passe 2 (mux): $cmd2")
        runFFmpeg(cmd2)
        tempVideo.delete()

        val finalPath = copyToDownloads(tempFinal, outputName)
        tempFinal.delete()
        AppLogger.log("FFmpeg", "succès → $finalPath")
        finalPath
    }

    private fun runFFmpeg(cmd: String) {
        val session = FFmpegKit.execute(cmd)
        val logs = session.allLogsAsString ?: ""
        AppLogger.log("FFmpeg", "rc=${session.returnCode} ${logs.takeLast(300)}")
        if (!ReturnCode.isSuccess(session.returnCode))
            throw RuntimeException("FFmpeg échoué\n${logs.takeLast(300)}")
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
