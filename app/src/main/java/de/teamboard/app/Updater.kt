package de.teamboard.app

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Prüft auf GitHub (Release "neu"), ob eine neuere App-Version bereitliegt, und lädt sie sicher herunter. */
object Updater {
    private const val BASE = "https://github.com/NaZAr465kras-sudo/teamboard-android/releases/download/neu/"

    data class Info(val code: Long, val name: String, val sha256: String)

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.instanceFollowRedirects = true
        return c
    }

    fun fetch(): Info? = try {
        val c = open(BASE + "version.json")
        if (c.responseCode != 200) null else {
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            Info(j.getLong("code"), j.getString("name"), j.getString("sha256").lowercase())
        }
    } catch (e: Exception) { null }

    /** Lädt die APK in den Cache und prüft die Prüfsumme. Gibt die Datei zurück oder null bei Fehler. */
    fun download(dir: File, info: Info, progress: (Int) -> Unit): File? = try {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "TeamBoard-update.apk")
        val c = open(BASE + "TeamBoard-NEU-WebView.apk")
        if (c.responseCode != 200) null else {
            val total = c.contentLengthLong
            val md = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var last = -1
            c.inputStream.use { ins ->
                out.outputStream().use { os ->
                    val buf = ByteArray(32768)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (total > 0) { val p = (done * 100 / total).toInt(); if (p != last) { last = p; progress(p) } }
                    }
                }
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            if (hex == info.sha256) out else { out.delete(); null }
        }
    } catch (e: Exception) { null }
}
