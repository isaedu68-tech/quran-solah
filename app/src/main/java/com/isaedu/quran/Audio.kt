package com.isaedu.quran

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Giọng đọc Mahmoud Khalil Al-Husary (Muallim – đọc chậm để học), từng āyah, tải về một lần rồi lưu trên máy. */
object Audio {
    private const val BASE = "https://everyayah.com/data/Husary_Muallim_128kbps/"
    private var player: MediaPlayer? = null

    private fun file(ctx: Context, s: Int, a: Int) = File(File(ctx.filesDir, "audio").apply { mkdirs() }, "%03d%03d.mp3".format(s, a))

    /** tải (nếu chưa có) và trả về file; null nếu không có mạng */
    suspend fun fetch(ctx: Context, s: Int, a: Int): File? = withContext(Dispatchers.IO) {
        val f = file(ctx, s, a)
        if (f.exists() && f.length() > 1000) return@withContext f
        runCatching {
            val c = URL(BASE + "%03d%03d.mp3".format(s, a)).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 30000
            val tmp = File(f.path + ".part")
            c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            tmp.renameTo(f); f
        }.getOrNull()
    }

    fun cachedCount(ctx: Context) = File(ctx.filesDir, "audio").listFiles()?.count { it.name.endsWith(".mp3") } ?: 0

    /** phát 1 file; onDone gọi khi phát xong hoặc lỗi */
    fun play(f: File, speed: Float, onDone: () -> Unit) {
        stop()
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setDataSource(f.path)
            mp.setOnCompletionListener { onDone() }
            mp.setOnErrorListener { _, _, _ -> onDone(); true }
            mp.prepare()
            if (speed != 1f) runCatching { mp.playbackParams = PlaybackParams().setSpeed(speed) }
            mp.start()
        } catch (e: Exception) { onDone() }
    }

    fun stop() { player?.let { runCatching { it.stop() }; it.release() }; player = null }
    val playing get() = runCatching { player?.isPlaying == true }.getOrDefault(false)
}
