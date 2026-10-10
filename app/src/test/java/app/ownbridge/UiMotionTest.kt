package app.ownbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class UiMotionTest {
    @Test fun flowDirectionFollowsStepOrder() {
        assertEquals(1f, ContentRoute.Step(Page.PLAN)
            .enterDirectionFrom(ContentRoute.Step(Page.CONNECT)))
        assertEquals(-1f, ContentRoute.Step(Page.CONNECT)
            .enterDirectionFrom(ContentRoute.Step(Page.PLAN)))
    }

    @Test fun settingsOpenAndBackFollowNavigationDepth() {
        assertEquals(1f, ContentRoute.Settings(SettingsDestination.HOME)
            .enterDirectionFrom(ContentRoute.Step(Page.RESULT)))
        assertEquals(1f, ContentRoute.Settings(SettingsDestination.LANGUAGE)
            .enterDirectionFrom(ContentRoute.Settings(SettingsDestination.HOME)))
        assertEquals(-1f, ContentRoute.Settings(SettingsDestination.HOME)
            .enterDirectionFrom(ContentRoute.Settings(SettingsDestination.LANGUAGE)))
        assertEquals(-1f, ContentRoute.Step(Page.CONNECT)
            .enterDirectionFrom(ContentRoute.Settings(SettingsDestination.HOME)))
    }
}
