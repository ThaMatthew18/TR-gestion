package uy.gestionpersonal

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePersistenceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun selectedThemeIsStoredAndSurvivesActivityRecreation() {
        compose.onNodeWithContentDescription("Cambiar tema").performClick()
        compose.onNodeWithText("Claro").performClick()
        compose.waitForIdle()
        assertEquals("LIGHT", compose.activity.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("theme_mode", null))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Cambiar tema").performClick()
        compose.onNodeWithText("Claro").assertIsSelected()
        compose.onNodeWithText("AMOLED").performClick()
        compose.waitForIdle()
        assertEquals("AMOLED", compose.activity.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("theme_mode", null))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Cambiar tema").performClick()
        compose.onNodeWithText("AMOLED").assertIsSelected()
    }
}
