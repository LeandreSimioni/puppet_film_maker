package eu.ueueue.muppet

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
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
        outputName: String = "muppet_${System.currentTimeMillis()}.mp4",
        introCardPath: String? = null,
        outroCardPath: String? = null
    ): String = withContext(Dispatchers.IO) {
        val frameCount = File(framesDir).listFiles()?.size ?: 0
        AppLogger.log("FFmpeg", "frames: $frameCount, intro: $introCardPath, outro: $outroCardPath")
        if (frameCount == 0) throw RuntimeException("Aucun frame JPEG dans $framesDir")

        // Passe 1 : vidéo principale
        val mainVid = File(context.cacheDir, "vid_main_${System.currentTimeMillis()}.mp4")
        runFFmpeg("-framerate $fps -i '$framesDir/frame_%04d.jpg' " +
                  "-vf 'scale=1080:1080' -c:v h264_mediacodec -b:v 6M " +
                  "-y '${mainVid.absolutePath}'")

        // Cases intro/outro — re-encodées à 1080×1080 pour compatibilité concat
        val introDuration = introCardPath?.let { getVideoDurationSeconds(it) } ?: 0.0
        val outroDuration  = outroCardPath?.let { getVideoDurationSeconds(it) }  ?: 0.0
        val introVid = introCardPath?.let { prepareCardVideo(it) }
        val outroVid  = outroCardPath?.let { prepareCardVideo(it) }

        // Concat vidéo : [intro?] + main + [outro?]
        val videoParts = listOfNotNull(introVid, mainVid, outroVid)
        val combinedVid = if (videoParts.size == 1) mainVid else concatVideos(videoParts)

        // Ajustement audio : silence calé sur la durée réelle de chaque case
        val finalAudio = if (audioPath != null && (introVid != null || outroVid != null)) {
            val parts = mutableListOf<File>()
            if (introVid != null) parts += createSilenceWav(introDuration)
            parts += File(audioPath)
            if (outroVid != null) parts += createSilenceWav(outroDuration)
            val out = File(context.cacheDir, "audio_cards_${System.currentTimeMillis()}.m4a").absolutePath
            concatenateAudio(parts, out)
            out
        } else audioPath

        // Passe 2 : mux vidéo + audio
        val tempFinal = File(context.cacheDir, outputName)
        val cmd2 = buildString {
            append("-i '${combinedVid.absolutePath}' ")
            if (finalAudio != null) append("-i '$finalAudio' ")
            append("-map 0:v ")
            if (finalAudio != null) append("-map 1:a ")
            append("-c:v copy ")
            if (finalAudio != null) append("-c:a aac -ar 44100 -shortest ")
            append("-y '${tempFinal.absolutePath}'")
        }
        runFFmpeg(cmd2)

        // Nettoyage
        if (combinedVid != mainVid) combinedVid.delete()
        introVid?.delete()
        outroVid?.delete()
        mainVid.delete()
        if (finalAudio != null && finalAudio != audioPath) File(finalAudio).delete()

        val finalPath = copyToDownloads(tempFinal, outputName)
        tempFinal.delete()
        AppLogger.log("FFmpeg", "succès → $finalPath")
        finalPath
    }

    private fun prepareCardVideo(videoPath: String): File {
        val out = File(context.cacheDir, "card_${System.currentTimeMillis()}.mp4")
        runFFmpeg("-i '$videoPath' -vf 'scale=1080:1080' -c:v h264_mediacodec -b:v 6M " +
                  "-y '${out.absolutePath}'")
        return out
    }

    private fun getVideoDurationSeconds(path: String): Double {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(path)
            val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            ms / 1000.0
        } catch (e: Exception) {
            AppLogger.err("FFmpeg", "Impossible de lire la durée de $path", e)
            0.0
        } finally {
            mmr.release()
        }
    }

    private fun concatVideos(parts: List<File>): File {
        val listFile = File(context.cacheDir, "vlist_${System.currentTimeMillis()}.txt")
        listFile.writeText(parts.joinToString("\n") { "file '${it.absolutePath}'" })
        val out = File(context.cacheDir, "vcombined_${System.currentTimeMillis()}.mp4")
        runFFmpeg("-f concat -safe 0 -i '${listFile.absolutePath}' -c copy -y '${out.absolutePath}'")
        listFile.delete()
        return out
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

    fun createSilenceWav(durationSeconds: Double): File {
        val sampleRate = 22050
        val numSamples = (durationSeconds * sampleRate).toInt().coerceAtLeast(1)
        val dataSize = numSamples * 2 // 16-bit mono
        val file = File(context.cacheDir, "silence_${System.currentTimeMillis()}.wav")
        file.outputStream().use { out ->
            fun writeInt(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun writeShort(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            out.write("RIFF".toByteArray())
            writeInt(36 + dataSize)
            out.write("WAVEfmt ".toByteArray())
            writeInt(16); writeShort(1); writeShort(1) // PCM, mono
            writeInt(sampleRate); writeInt(sampleRate * 2) // byteRate
            writeShort(2); writeShort(16) // blockAlign, bitsPerSample
            out.write("data".toByteArray())
            writeInt(dataSize)
            out.write(ByteArray(dataSize)) // silence = zeros
        }
        return file
    }

    fun concatenateAudio(parts: List<File>, outputPath: String) {
        if (parts.size == 1) { parts[0].copyTo(File(outputPath), overwrite = true); return }
        val listFile = File(context.cacheDir, "concat_${System.currentTimeMillis()}.txt")
        listFile.writeText(parts.joinToString("\n") { "file '${it.absolutePath}'" })
        runFFmpeg("-f concat -safe 0 -i '${listFile.absolutePath}' -c:a aac -ar 22050 -y '$outputPath'")
        listFile.delete()
    }

    data class SilenceInsertion(val atSeconds: Double, val duration: Double)

    fun applyAudioInsertions(audioPath: String, insertions: List<SilenceInsertion>): String {
        if (insertions.isEmpty()) return audioPath
        val sorted = insertions.sortedBy { it.atSeconds }
        val parts = mutableListOf<File>()
        var prev = 0.0
        for (ins in sorted) {
            val seg = File(context.cacheDir, "aseg_${System.currentTimeMillis()}_${parts.size}.wav")
            runFFmpeg("-i '$audioPath' -ss $prev -to ${ins.atSeconds} -ar 22050 -ac 1 '${seg.absolutePath}'")
            parts += seg
            parts += createSilenceWav(ins.duration)
            prev = ins.atSeconds
        }
        val last = File(context.cacheDir, "aseg_${System.currentTimeMillis()}_last.wav")
        runFFmpeg("-i '$audioPath' -ss $prev -ar 22050 -ac 1 '${last.absolutePath}'")
        parts += last
        val out = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a").absolutePath
        concatenateAudio(parts, out)
        parts.forEach { it.delete() }
        AppLogger.log("FFmpeg", "audio modifié avec ${insertions.size} silence(s) → $out")
        return out
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
