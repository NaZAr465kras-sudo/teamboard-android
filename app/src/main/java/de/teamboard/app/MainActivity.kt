package de.teamboard.app

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat

/**
 * Startet das Team-Board im Chrome-Vollbild-Tab (Custom Tab).
 * Mikrofon, Anmeldung bei Claude und Sprachfunktionen laufen dabei wie in Chrome.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.open).setOnClickListener { openBoard() }
        // Beim ersten Start sofort öffnen, danach zeigt die App den Knopf zum erneuten Öffnen.
        if (savedInstanceState == null) openBoard()
    }

    private fun openBoard() {
        val tab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .setToolbarColor(ContextCompat.getColor(this, R.color.brand))
            .build()
        tab.launchUrl(this, Uri.parse(BOARD_URL))
    }

    companion object {
        const val BOARD_URL = "https://claude.ai/artifact/6hbBGZifppeX6tGX7EGH7q"
    }
}
