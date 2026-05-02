package eu.ueueue.muppet

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private var logFile: File? = null
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun init(context: Context) {
        logFile = File(context.cacheDir, "muppet.log").also {
            if (it.length() > 300_000) it.delete()
        }
        log("App", "=== session démarrée ===")
    }

    fun log(tag: String, msg: String) {
        val line = "[${fmt.format(Date())}] $tag: $msg\n"
        Log.d(tag, msg)
        logFile?.appendText(line)
    }

    fun err(tag: String, msg: String, e: Throwable? = null) {
        val trace = e?.stackTraceToString()?.take(800) ?: ""
        val line = "[${fmt.format(Date())}] ERR $tag: $msg\n$trace\n"
        Log.e(tag, msg, e)
        logFile?.appendText(line)
    }

    fun read(): String = try {
        logFile?.readText()?.takeLast(8000) ?: "Aucun log."
    } catch (e: Exception) {
        "Impossible de lire le log : ${e.message}"
    }
}
