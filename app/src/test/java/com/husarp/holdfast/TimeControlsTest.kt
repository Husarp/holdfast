package com.husarp.holdfast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.husarp.holdfast.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimeControlsTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun daysToggleWithoutTypingAndStaySorted() {
        var days by mutableStateOf("1,2,3,4,5")
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.get().setContent { HoldfastTheme(dynamic = false) { DayChips(days) { days = it } } }
        compose.onNodeWithText("Mon").performClick()
        assertEquals("2,3,4,5", days)
        compose.onNodeWithText("Sun").performClick()
        assertEquals("2,3,4,5,7", days)
        compose.onNodeWithText("Mon").performClick()
        assertEquals("1,2,3,4,5,7", days)
        activity.pause().stop().destroy()
    }

    @Test fun customMinutesRejectInvalidInputAndPreserveValidValue() {
        var minutes by mutableStateOf("15")
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.get().setContent { HoldfastTheme(dynamic = false) { MinuteChoices("Repeat", minutes, { minutes = it }, emptyLabel = "Off") } }
        compose.onNodeWithText("60 min").performClick()
        assertEquals("60", minutes)
        compose.onNodeWithText("Custom…").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("0")
        compose.onNodeWithText("OK").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("42")
        compose.onNodeWithText("OK").performClick()
        assertEquals("42", minutes)
        compose.onNodeWithText("Off").performClick()
        assertEquals("", minutes)
        activity.pause().stop().destroy()
    }
}
