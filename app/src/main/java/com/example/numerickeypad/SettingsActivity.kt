package com.example.numerickeypad

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.graphics.Typeface
import android.widget.TextView
import android.graphics.Color
import android.view.View
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class SettingsActivity : AppCompatActivity() {
    private val GITHUB_URL = "https://github.com/Spectre71/numericKeypadApp"
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }
    private val supportedLanguages = listOf(
        Language.EN to "English",
        Language.SL to "Slovenščina"
    )
    private val supportedThemes = listOf(
        AppTheme.DEFAULT,
        AppTheme.TWO_TONE,
        AppTheme.PROFESSIONAL
    )

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val PREF_LEFT_HANDED_MODE = "left_handed_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: allow app content behind system bars.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_settings)
        supportActionBar?.hide()

        // Make system bars transparent and keep icons visible over dark UI.
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
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
                window.decorView.systemUiVisibility and
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and
                        View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }

        // Wire up language/handedness toggles in Settings screen
        val btn = findViewById<Button>(R.id.languageToggleButton)
        val handednessBtn = findViewById<Button>(R.id.handednessToggleButton)
        val themeBtn = findViewById<Button>(R.id.themeSelectButton)
        applyThemeToUi()
        refreshSettingsTexts()
        updateLanguageButtonLabel(btn)
        updateHandednessButtonLabel(handednessBtn)
        updateThemeButtonLabel(themeBtn)

        btn.setOnClickListener {
            showLanguageMenu(languageButton = btn, handednessButton = handednessBtn, themeButton = themeBtn)
        }

        handednessBtn.setOnClickListener {
            showHandednessMenu(handednessButton = handednessBtn)
        }

        themeBtn.setOnClickListener {
            showThemeMenu(themeButton = themeBtn)
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
        applyThemeToUi()
        refreshSettingsTexts()
        findViewById<Button>(R.id.languageToggleButton)?.let { updateLanguageButtonLabel(it) }
        findViewById<Button>(R.id.handednessToggleButton)?.let { updateHandednessButtonLabel(it) }
        findViewById<Button>(R.id.themeSelectButton)?.let { updateThemeButtonLabel(it) }
    }

    private fun refreshSettingsTexts() {
        findViewById<TextView>(R.id.settingsTitle)?.text = Translator.t("Interface")
        findViewById<TextView>(R.id.languageLabel)?.text = Translator.t("Language:")
        findViewById<TextView>(R.id.handednessLabel)?.text = Translator.t("Handedness:")
        findViewById<TextView>(R.id.themeLabel)?.text = Translator.t("Theme:")
        findViewById<TextView>(R.id.aboutTitle)?.text = Translator.t("About")
    }

    private fun updateLanguageButtonLabel(button: Button) {
        val currentLanguage = Translator.getLanguage()
        button.text = supportedLanguages.firstOrNull { it.first == currentLanguage }?.second ?: "English"
    }

    private fun updateHandednessButtonLabel(button: Button, isLeftHanded: Boolean = prefs.getBoolean(PREF_LEFT_HANDED_MODE, false)) {
        button.text = if (isLeftHanded) {
            Translator.t("Left-handed")
        } else {
            Translator.t("Right-handed")
        }
    }

    private fun updateThemeButtonLabel(button: Button) {
        button.text = Translator.t(AppThemeManager.getTheme(this).displayName)
    }

    private fun showLanguageMenu(languageButton: Button, handednessButton: Button, themeButton: Button) {
        val labels = supportedLanguages.map { it.second }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(Translator.t("Select language"))
            .setItems(labels) { _, which ->
                val selected = supportedLanguages.getOrNull(which)?.first ?: return@setItems
                if (selected != Translator.getLanguage()) {
                    Translator.setLanguage(selected)
                    prefs.edit().putString("lang", if (selected == Language.SL) "SL" else "EN").apply()
                    Toast.makeText(this, Translator.t("Language updated"), Toast.LENGTH_SHORT).show()
                }
                updateLanguageButtonLabel(languageButton)
                updateHandednessButtonLabel(handednessButton)
                updateThemeButtonLabel(themeButton)
                refreshSettingsTexts()
            }
            .setNegativeButton(Translator.t("Cancel"), null)
            .show()
    }

    private fun showHandednessMenu(handednessButton: Button) {
        val options = arrayOf(
            Translator.t("Right-handed"),
            Translator.t("Left-handed")
        )
        AlertDialog.Builder(this)
            .setTitle(Translator.t("Select handedness"))
            .setItems(options) { _, which ->
                val newValue = which == 1
                val current = prefs.getBoolean(PREF_LEFT_HANDED_MODE, false)
                prefs.edit().putBoolean(PREF_LEFT_HANDED_MODE, newValue).commit()
                updateHandednessButtonLabel(handednessButton, newValue)
                refreshSettingsTexts()
                if (current != newValue) {
                    Toast.makeText(this, Translator.t("Handedness updated"), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(Translator.t("Cancel"), null)
            .show()
    }

    private fun showThemeMenu(themeButton: Button) {
        val options = supportedThemes.map { Translator.t(it.displayName) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(Translator.t("Select theme"))
            .setItems(options) { _, which ->
                val selected = supportedThemes.getOrNull(which) ?: return@setItems
                val current = AppThemeManager.getTheme(this)
                AppThemeManager.setTheme(this, selected)
                updateThemeButtonLabel(themeButton)
                applyThemeToUi()
                if (current != selected) {
                    Toast.makeText(this, Translator.t("Theme updated"), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(Translator.t("Cancel"), null)
            .show()
    }

    private fun applyThemeToUi() {
        val palette = AppThemeManager.getPalette(AppThemeManager.getTheme(this))
        findViewById<View>(android.R.id.content)?.setBackgroundColor(palette.appBackground)

        listOf(
            R.id.settingsTitle,
            R.id.languageLabel,
            R.id.handednessLabel,
            R.id.themeLabel,
            R.id.aboutTitle,
            R.id.githubLink
        ).forEach { id ->
            findViewById<TextView>(id)?.setTextColor(palette.settingsText)
        }

        listOf(
            R.id.dividerSettingsHeader,
            R.id.dividerAboutHeader
        ).forEach { id ->
            findViewById<View>(id)?.setBackgroundColor(palette.divider)
        }

        listOf(
            R.id.dividerAfterLanguage,
            R.id.dividerAfterTheme,
            R.id.dividerAfterHandedness
        ).forEach { id ->
            findViewById<View>(id)?.setBackgroundColor(palette.divider_accent)
        }

        listOf(
            R.id.languageToggleButton,
            R.id.handednessToggleButton,
            R.id.themeSelectButton
        ).forEach { id ->
            findViewById<Button>(id)?.backgroundTintList = ColorStateList.valueOf(palette.keypadButton)
        }
    }
}
