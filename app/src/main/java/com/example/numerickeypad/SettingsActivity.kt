package com.example.numerickeypad

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.graphics.Typeface
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.graphics.Color
import android.view.View
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private val GITHUB_URL = "https://github.com/Spectre71/numericKeypadApp"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.hide()

        // Match system bars to app background in Settings as well
        val appBg = ContextCompat.getColor(this, R.color.app_background)
        window.navigationBarColor = appBg
        window.statusBarColor = appBg
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val controller = window.insetsController
            controller?.setSystemBarsAppearance(
                /* appearance = */ 0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }

        // Wire up language toggle in Settings screen
    val btn = findViewById<Button>(R.id.languageToggleButton)
    // Settings title and labels
    findViewById<TextView>(R.id.settingsTitle)?.text = Translator.t("Interface")
    findViewById<TextView>(R.id.languageLabel)?.text = Translator.t("Language:")
    findViewById<TextView>(R.id.aboutTitle)?.text = Translator.t("About")
        updateLanguageButtonLabel(btn)

        btn.setOnClickListener {
            val newLang = if (Translator.getLanguage() == Language.SL) Language.EN else Language.SL
            Translator.setLanguage(newLang)
            getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit().putString("lang", if (newLang == Language.SL) "SL" else "EN").apply()
            updateLanguageButtonLabel(btn)
            // Update headings after switching
            findViewById<TextView>(R.id.settingsTitle)?.text = Translator.t("Interface")
            findViewById<TextView>(R.id.languageLabel)?.text = Translator.t("Language:")
            findViewById<TextView>(R.id.aboutTitle)?.text = Translator.t("About")
            Toast.makeText(this, Translator.t("Language updated"), Toast.LENGTH_SHORT).show()
        }

        // GitHub link icon (uses Font Awesome Brands if present in assets/fonts/fa-brands-400.ttf)
        val githubView = findViewById<TextView>(R.id.githubLink)
        try {
            val tf = Typeface.createFromAsset(assets, "fonts/fa-brands-400.ttf")
            githubView?.typeface = tf
        } catch (_: Throwable) { /* font optional; keep default if missing */ }

        githubView?.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-apply current language on resume
        findViewById<TextView>(R.id.settingsTitle)?.text = Translator.t("Interface")
        findViewById<TextView>(R.id.languageLabel)?.text = Translator.t("Language:")
        findViewById<TextView>(R.id.aboutTitle)?.text = Translator.t("About")
        findViewById<Button>(R.id.languageToggleButton)?.let { updateLanguageButtonLabel(it) }
    }

    private fun updateLanguageButtonLabel(button: Button) {
        button.text = if (Translator.getLanguage() == Language.SL) "Slovenščina" else "English"
    }
}
