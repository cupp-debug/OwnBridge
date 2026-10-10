package app.ownbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun recognizesSupportedLocaleVariants() {
        assertEquals(AppLanguage.CHINESE, AppLanguage.fromTags("zh-CN"))
        assertEquals(AppLanguage.CHINESE, AppLanguage.fromTags("zh-Hans-CN"))
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.fromTags("zh-TW"))
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.fromTags("zh-Hant-HK"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("en-US"))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.fromTags("ru-RU"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTags("es-ES"))
        assertEquals(AppLanguage.TURKISH, AppLanguage.fromTags("tr-TR"))
        assertEquals(AppLanguage.PERSIAN, AppLanguage.fromTags("fa-IR"))
    }

    @Test fun followsSystemForEmptyOrUnsupportedLocales() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags("fr-FR"))
    }
}
