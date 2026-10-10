package app.ownbridge

import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

internal enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    CHINESE("zh-CN"),
    TRADITIONAL_CHINESE("zh-TW"),
    ENGLISH("en"),
    RUSSIAN("ru"),
    SPANISH("es"),
    TURKISH("tr"),
    PERSIAN("fa");

    companion object {
        fun fromTags(tags: String): AppLanguage = when {
            tags.startsWith("zh-TW", ignoreCase = true) ||
                tags.startsWith("zh-Hant", ignoreCase = true) ||
                tags.startsWith("zh-HK", ignoreCase = true) ||
                tags.startsWith("zh-MO", ignoreCase = true) -> TRADITIONAL_CHINESE
            tags.startsWith("zh", ignoreCase = true) -> CHINESE
            tags.startsWith("en", ignoreCase = true) -> ENGLISH
            tags.startsWith("ru", ignoreCase = true) -> RUSSIAN
            tags.startsWith("es", ignoreCase = true) -> SPANISH
            tags.startsWith("tr", ignoreCase = true) -> TURKISH
            tags.startsWith("fa", ignoreCase = true) -> PERSIAN
            else -> SYSTEM
        }
    }
}

/** Android 13+ owns the app locale; older devices keep the choice in private preferences. */
internal class AppLanguagePreference(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("app_language", Context.MODE_PRIVATE)

    fun current(): AppLanguage {
        if (Build.VERSION.SDK_INT < 33) {
            return AppLanguage.fromTags(preferences.getString("tag", "").orEmpty())
        }
        val manager = appContext.getSystemService(LocaleManager::class.java)
        if (!preferences.getBoolean("migrated_to_system", false)) {
            val oldChoice = AppLanguage.fromTags(preferences.getString("tag", "").orEmpty())
            if (manager.applicationLocales.isEmpty && oldChoice != AppLanguage.SYSTEM) {
                manager.applicationLocales = LocaleList.forLanguageTags(oldChoice.tag)
            }
            preferences.edit().putBoolean("migrated_to_system", true).apply()
        }
        return AppLanguage.fromTags(manager.applicationLocales.toLanguageTags())
    }

    fun select(language: AppLanguage): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            return runCatching {
                appContext.getSystemService(LocaleManager::class.java).applicationLocales =
                    LocaleList.forLanguageTags(language.tag)
                preferences.edit().putBoolean("migrated_to_system", true).apply()
            }.isSuccess
        }
        return preferences.edit().putString("tag", language.tag).commit()
    }
}

/** Replaces only UI resources, without recreating deployment controllers or their live state. */
@Composable
internal fun LocalizedContent(language: AppLanguage, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val localizedContext = remember(baseContext, baseConfiguration, language) {
        if (language == AppLanguage.SYSTEM) baseContext else {
            val locale = Locale.forLanguageTag(language.tag)
            val configuration = Configuration(baseConfiguration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            val resources = baseContext.createConfigurationContext(configuration).resources
            object : ContextWrapper(baseContext) {
                override fun getResources(): Resources = resources
            }
        }
    }
    val layoutDirection = if (
        localizedContext.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    ) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalResources provides localizedContext.resources,
        LocalLayoutDirection provides layoutDirection,
        content = content,
    )
}
