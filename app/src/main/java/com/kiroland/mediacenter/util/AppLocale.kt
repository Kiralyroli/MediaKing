package com.kiroland.mediacenter.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import java.util.Locale

/** The app's languages; [SYSTEM] follows the TV's language when it is one of these, else English. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    HUNGARIAN("hu"),
    ENGLISH("en"),
    GERMAN("de"),
}

/**
 * The language the app shows itself in, chosen in the settings. Read straight from the preferences
 * (activities need it before Hilt is up) and applied by wrapping each activity's context.
 */
object AppLocale {
    private const val PREFS = "settings"
    private const val KEY = "app_language"
    private val SUPPORTED = setOf("hu", "en", "de")

    @Volatile
    private var cached: Locale? = null

    @Volatile
    private var resources: Resources? = null

    /** Stands in for Android resources in unit tests. */
    @VisibleForTesting
    var testStrings: ((Int, Array<out Any>) -> String)? = null

    /** Unit tests have no context to read the language from; they set it here. */
    @VisibleForTesting
    fun useForTests(locale: Locale) {
        cached = locale
    }

    /** Called once from the application, so [text] works outside activities. */
    fun init(app: Context) {
        resources = wrap(app).resources
    }

    /** A string in the app's language for code without an activity at hand (servers, repositories). */
    fun text(@StringRes id: Int, vararg args: Any): String =
        testStrings?.invoke(id, args) ?: requireNotNull(resources) { "AppLocale.init not called" }.getString(id, *args)

    fun language(context: Context): AppLanguage =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { saved -> AppLanguage.entries.firstOrNull { it.name == saved } } ?: AppLanguage.SYSTEM

    fun setLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.name).commit()
        cached = null
        resources = wrap(context.applicationContext).resources
    }

    /** The effective locale: the chosen language, or the system's if supported, else English. */
    fun locale(context: Context): Locale {
        cached?.let { return it }
        val tag = language(context).tag
            // The TV's own setting: Locale.getDefault() is ours once a language has been applied.
            ?: Resources.getSystem().configuration.locales.get(0).language.takeIf { it in SUPPORTED }
            ?: "en"
        return Locale.forLanguageTag(tag).also {
            cached = it
            Locale.setDefault(it)
        }
    }

    /** The locale once known, for formatting where no context is at hand. */
    val current: Locale get() = cached ?: Locale.ENGLISH

    /** TMDB's language parameter for texts (titles, overviews) in the app's language. */
    val tmdbLanguage: String
        get() = when (current.language) {
            "en" -> "en-US"
            "de" -> "de-DE"
            else -> "hu-HU"
        }

    /** [base] with the app's language, for an activity's attachBaseContext or non-UI strings. */
    fun wrap(base: Context): Context {
        val locale = locale(base)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
