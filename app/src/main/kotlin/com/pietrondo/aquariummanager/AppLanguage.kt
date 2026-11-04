package com.pietrondo.aquariummanager

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi

/**
 * In-app language override. Android 13+ stores a per-app locale, so the choice survives
 * restarts and is also exposed in the system settings page.
 */
object AppLanguage {
    /** Per-app locales exist only from API 33. */
    val isSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Selected language tag, or null when the app follows the system locale. */
    fun current(context: Context): String? {
        if (!isSupported) return null
        val locales = locales(context) ?: return null
        if (locales.isEmpty) return null
        return locales[0]?.language
    }

    /** Pass a language tag such as "it" or "en", or null to follow the system locale. */
    fun select(context: Context, language: String?) {
        if (!isSupported) return
        val manager = manager(context) ?: return
        manager.applicationLocales = if (language == null) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(language)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun locales(context: Context): LocaleList? =
        manager(context)?.applicationLocales

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun manager(context: Context): LocaleManager? =
        context.getSystemService(LocaleManager::class.java)
}
