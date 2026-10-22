package com.arcanatable.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.arcanatable.CardZoneDialog
import com.arcanatable.GameCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w344dp-h882dp-port-xxhdpi")
class ZoneCardActionsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val spell = GameCard(1, "Graveyard Spell", "Sorcery", Color.White)

    @Test fun sandboxZoneOffersManualPlayAction() {
        var selected: Int? = null
        rule.setContent {
            CardZoneDialog("Graveyard", "YOU", listOf(spell),
                onDismiss = {}, onPreview = {}, manualPlayAllowed = true,
                onPlayCard = { selected = it.id })
        }
        rule.onNodeWithText("PLAY / CAST · MANUAL").assertExists().performClick()
        rule.runOnIdle { assertEquals(1, selected) }
    }

    @Test fun argentumZoneShowsActionsOnlyForCardsEnginePermits() {
        val other = GameCard(2, "Unplayable Card", "Instant", Color.White)
        var selected: Int? = null
        rule.setContent {
            CardZoneDialog("Exile", "YOU", listOf(spell, other),
                onDismiss = {}, onPreview = {},
                argentumPlayableIds = setOf(spell.id),
                onPlayCard = { selected = it.id })
        }
        rule.onNodeWithText("LEGAL ACTIONS").assertExists().performClick()
        rule.runOnIdle { assertEquals(spell.id, selected) }
        rule.onNodeWithText("PLAY / CAST · MANUAL").assertDoesNotExist()
    }

    @Test fun unplayableEngineCardHasNoMisleadingCastButton() {
        rule.setContent {
            CardZoneDialog("Graveyard", "YOU", listOf(spell),
                onDismiss = {}, onPreview = {}, argentumPlayableIds = emptySet())
        }
        rule.onNodeWithText("LEGAL ACTIONS").assertDoesNotExist()
        rule.onNodeWithText("PLAY / CAST · MANUAL").assertDoesNotExist()
    }
}
