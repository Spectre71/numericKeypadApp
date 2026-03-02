package com.example.numerickeypad

import android.content.Context
import android.graphics.Color
import androidx.annotation.DrawableRes

enum class AppTheme(val prefValue: String, val displayName: String) {
    DEFAULT("default", "Default"),
    TWO_TONE("2-tone", "2-Tone"),
    PROFESSIONAL("professional", "Professional");

    companion object {
        fun fromPrefValue(value: String?): AppTheme = entries.firstOrNull { it.prefValue == value } ?: DEFAULT
    }
}

data class ThemePalette(
    val appBackground: Int,
    val statusText: Int,
    val settingsText: Int,
    val divider: Int,
    val divider_accent: Int,
    val connectButton: Int,
    val escButton: Int,
    val keypadButton: Int,
    val key5Button: Int,
    val enterButton: Int,
    val backspaceButton: Int,
    val upDownButton: Int,
    val trackpadButton: Int,
    val numLockButton: Int,
    val mouseButton: Int,
    val closeTrackpadButton: Int,
    @DrawableRes val trackpadSurfaceDrawable: Int
)

object AppThemeManager {
    private const val PREFS_NAME = "app_prefs"
    private const val PREF_THEME = "app_theme"

    fun getTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppTheme.fromPrefValue(prefs.getString(PREF_THEME, AppTheme.DEFAULT.prefValue))
    }

    fun setTheme(context: Context, theme: AppTheme) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_THEME, theme.prefValue).apply()
    }

    fun getPalette(theme: AppTheme): ThemePalette = when (theme) {
        AppTheme.DEFAULT -> ThemePalette(
            appBackground = Color.parseColor("#1E1E1E"),
            statusText = Color.parseColor("#AAAAAA"),
            settingsText = Color.WHITE,
            divider = Color.parseColor("#6E27C4"),
            divider_accent = Color.parseColor("#424242"),
            connectButton = Color.parseColor("#4CAF50"),
            escButton = Color.parseColor("#D42222"),
            keypadButton = Color.parseColor("#3F51B5"),
            key5Button = Color.parseColor("#5A6BCC"),
            enterButton = Color.parseColor("#257D29"),
            backspaceButton = Color.parseColor("#FF5722"),
            upDownButton = Color.parseColor("#FF9800"),
            trackpadButton = Color.parseColor("#6443DC"),
            numLockButton = Color.parseColor("#8843dc"),
            mouseButton = Color.parseColor("#9C27B0"),
            closeTrackpadButton = Color.parseColor("#424242"),
            trackpadSurfaceDrawable = R.drawable.rounded_trackpad
        )

        AppTheme.TWO_TONE -> ThemePalette(
            appBackground = Color.parseColor("#000000"),
            statusText = Color.parseColor("#AAAAAA"),
            settingsText = Color.WHITE,
            divider = Color.parseColor("#AAAAAA"),
            divider_accent = Color.parseColor("#424242"),
            connectButton = Color.parseColor("#2D6A4F"),
            escButton = Color.parseColor("#EF476F"),
            keypadButton = Color.parseColor("#1F2E46"),
            key5Button = Color.parseColor("#2C3F5D"),
            enterButton = Color.parseColor("#2D6A4F"),
            backspaceButton = Color.parseColor("#EF476F"),
            upDownButton = Color.parseColor("#734cb1"),
            trackpadButton = Color.parseColor("#2D6A4F"),
            numLockButton = Color.parseColor("#EF476F"),
            mouseButton = Color.parseColor("#734cb1"),
            closeTrackpadButton = Color.parseColor("#3A4352"),
            trackpadSurfaceDrawable = R.drawable.rounded_trackpad_two_tone
        )

        AppTheme.PROFESSIONAL -> ThemePalette(
            appBackground = Color.parseColor("#3d3d3d"),
            statusText = Color.parseColor("#AAAAAA"),
            settingsText = Color.WHITE,
            divider = Color.parseColor("#B88A2A"),
            divider_accent = Color.parseColor("#a8a8a8"),
            connectButton = Color.parseColor("#538a4e"),
            escButton = Color.parseColor("#9b4444"),
            keypadButton = Color.parseColor("#7C6A9A"),
            key5Button = Color.parseColor("#9A88B8"),
            enterButton = Color.parseColor("#6D8F6A"),
            backspaceButton = Color.parseColor("#8C5E5E"),
            upDownButton = Color.parseColor("#b4a1dd"),
            trackpadButton = Color.parseColor("#8A7AA6"),
            numLockButton = Color.parseColor("#8A7AA6"),
            mouseButton = Color.parseColor("#9C8AC0"),
            closeTrackpadButton = Color.parseColor("#B8AFBF"),
            trackpadSurfaceDrawable = R.drawable.rounded_trackpad_professional
        )
    }
}
