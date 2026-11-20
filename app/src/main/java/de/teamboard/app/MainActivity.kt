package de.teamboard.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.File

/** Team-Board als eigenständige App: Oberfläche liegt in der App, Daten kommen aus Firebase. */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private var kds: WebView? = null
    private var kdsUrl = ""
    private var kdsBox = ""
    private var recognizer: SpeechRecognizer? = null
    private var wantListen = false

    // ---- Foto-Auswahl (Galerie/Kamera) für <input type=file> ----
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var camUri: Uri? = null
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val cb = fileCb; fileCb = null
        var out: Array<Uri>? = null
        if (res.resultCode == RESULT_OK) {
            val d = res.data
            out = when {
                d?.data != null -> arrayOf(d.data!!)
                d?.clipData != null && d.clipData!!.itemCount > 0 -> arrayOf(d.clipData!!.getItemAt(0).uri)
                camUri != null -> arrayOf(camUri!!)
                else -> null
            }
        }
        camUri = null
        cb?.onReceiveValue(out)
    }

    private fun js(type: String, text: String) {
        val a = JSONObject.quote(type)
        val b = JSONObject.quote(text)
        runOnUiThread { web.evaluateJavascript("window.__stt&&window.__stt($a,$b)", null) }
    }

    inner class Bridge {
        @JavascriptInterface
        fun startListening() = runOnUiThread { beginListen() }

        @JavascriptInterface
        fun stopListening() = runOnUiThread { recognizer?.stopListening() }

        @JavascriptInterface
        fun kdsShow(url: String, x: Double, y: Double, w: Double, h: Double) = runOnUiThread { showKds(url, x, y, w, h) }

        @JavascriptInterface
        fun kdsReload() = runOnUiThread { kds?.reload() }

        @JavascriptInterface
        fun kdsHide() = runOnUiThread { kds?.visibility = View.GONE }

        @JavascriptInterface
        fun saveFile(name: String, data: String) = runOnUiThread { writeFile(name, data) }
    }

    // Küchen-Display: eigene WebView über dem Inhaltsbereich (eigener, dauerhafter Speicher → Kopplung bleibt erhalten)
    @SuppressLint("SetJavaScriptEnabled")
    private fun showKds(url: String, x: Double, y: Double, w: Double, h: Double) {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return
        val d = resources.displayMetrics.density
        var v = kds
        if (v == null) {
            v = WebView(this).apply {
                setBackgroundColor(Color.WHITE)
                with(settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                webViewClient = WebViewClient()
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            }
            root.addView(v, FrameLayout.LayoutParams(1, 1))
            kds = v
        }
        val key = "$x,$y,$w,$h"
        if (key != kdsBox) {
            val lp = FrameLayout.LayoutParams(maxOf(1, (w * d).toInt()), maxOf(1, (h * d).toInt()))
            lp.leftMargin = (x * d).toInt()
            lp.topMargin = (y * d).toInt()
            v.layoutParams = lp
            kdsBox = key
        }
        if (url != kdsUrl) { kdsUrl = url; v.loadUrl(url) }
        v.visibility = View.VISIBLE
    }

    private fun beginListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            wantListen = true
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 7)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Spracherkennung nicht verfügbar", Toast.LENGTH_SHORT).show()
            js("state", "idle")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) = js("state", "listening")
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(e: Int) = js("state", "idle")
                override fun onPartialResults(b: Bundle?) {
                    b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { js("partial", it) }
                }
                override fun onEvent(t: Int, p: Bundle?) {}
                override fun onResults(b: Bundle?) {
                    b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { js("final", it) }
                    js("state", "idle")
                }
            })
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            })
        }
    }

    private fun writeFile(name: String, data: String) {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        try {
            val bytes = ("﻿" + data).toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safe)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
                contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
                Toast.makeText(this, "Gespeichert in Downloads: $safe", Toast.LENGTH_LONG).show()
            } else {
                val f = File(getExternalFilesDir(null), safe)
                f.writeBytes(bytes)
                Toast.makeText(this, "Gespeichert: ${f.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Speichern fehlgeschlagen", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#111111")
        window.navigationBarColor = Color.parseColor("#111111")

        web = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#111111"))
        }
        root = FrameLayout(this)
        root.addView(web)
        setContentView(root)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.addJavascriptInterface(Bridge(), "AndroidBridge")

        // Die Oberfläche liegt als Datei in der App und wird über eine echte https-Adresse ausgeliefert.
        val loader = WebViewAssetLoader.Builder()
            .setDomain(HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = cb
                try {
                    val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }
                    val extra = mutableListOf<Intent>()
                    try {
                        val dir = File(cacheDir, "photos").apply { mkdirs() }
                        val f = File(dir, "p" + System.currentTimeMillis() + ".jpg")
                        val u = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", f)
                        camUri = u
                        extra.add(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                            putExtra(MediaStore.EXTRA_OUTPUT, u)
                            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        })
                    } catch (_: Exception) {}
                    val ch = Intent.createChooser(pick, "Foto").putExtra(Intent.EXTRA_INITIAL_INTENTS, extra.toTypedArray())
                    picker.launch(ch)
                } catch (e: Exception) {
                    fileCb = null
                    cb.onReceiveValue(null)
                    return false
                }
                return true
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(r.url)

            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                if (!r.isForMainFrame || r.url.host == HOST) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, r.url)) } catch (_: Exception) {}
                return true
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val k = kds
                if (k != null && k.visibility == View.VISIBLE && k.canGoBack()) k.goBack()
                else if (web.canGoBack()) web.goBack() else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })

        if (savedInstanceState == null) web.loadUrl("https://$HOST/assets/index.html") else web.restoreState(savedInstanceState)
    }


    // ---- Automatische App-Updates (GitHub Release "neu") ----
    private var lastCheck = 0L
    private var pendingApk: File? = null
    private var checking = false

    private fun checkUpdate(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (checking || (!force && now - lastCheck < 3_600_000)) return
        checking = true
        lastCheck = now
        Thread {
            val info = Updater.fetch()
            val mine = try { PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0)) } catch (e: Exception) { Long.MAX_VALUE }
            runOnUiThread {
                checking = false
                if (info != null && info.code > mine && !isFinishing) askUpdate(info)
            }
        }.start()
    }

    private fun askUpdate(info: Updater.Info) {
        AlertDialog.Builder(this)
            .setTitle("Update verfügbar")
            .setMessage("Neue Version ${info.name} ist bereit. Jetzt aktualisieren? Deine Daten bleiben erhalten.")
            .setPositiveButton("Jetzt aktualisieren") { _, _ -> startDownload(info) }
            .setNegativeButton("Später", null)
            .show()
    }

    private fun startDownload(info: Updater.Info) {
        val dlg = AlertDialog.Builder(this).setTitle("Update").setMessage("Lade herunter … 0 %").setCancelable(false).create()
        dlg.show()
        Thread {
            val f = Updater.download(File(cacheDir, "updates"), info) { p -> runOnUiThread { dlg.setMessage("Lade herunter … $p %") } }
            runOnUiThread {
                dlg.dismiss()
                if (f == null) Toast.makeText(this, "Update fehlgeschlagen. Bitte später erneut versuchen.", Toast.LENGTH_LONG).show()
                else installApk(f)
            }
        }.start()
    }

    private fun installApk(f: File) {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            pendingApk = f
            Toast.makeText(this, "Bitte „Installation erlauben“ aktivieren und zurückkehren.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        pendingApk = null
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    override fun onResume() {
        super.onResume()
        val p = pendingApk
        if (p != null && p.exists() && (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls())) installApk(p)
        else checkUpdate()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 7) {
            if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED && wantListen) beginListen() else js("state", "idle")
            wantListen = false
        }
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); web.saveState(out) }
    override fun onPause() { super.onPause(); CookieManager.getInstance().flush() }
    override fun onStop() { super.onStop(); CookieManager.getInstance().flush() }
    override fun onDestroy() { recognizer?.destroy(); kds?.destroy(); web.destroy(); super.onDestroy() }

    companion object { const val HOST = "appassets.androidplatform.net" }
}
