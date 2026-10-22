package com.arcanatable.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.arcanatable.GameCard
import com.arcanatable.PlayerOverview
import com.arcanatable.TablePlayer
import com.arcanatable.boardZoomKey
import com.arcanatable.tableZoomKey
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TableOverviewInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test @Config(sdk = [34], qualifiers = "w344dp-h882dp-port-xxhdpi")
    fun playerPanelsOnlyExpandWhenTappedAndStayOpenAcrossTurnChanges() {
        rule.setContent {
            var active by remember { mutableStateOf(true) }
            val zoom = remember { mutableFloatStateOf(1f) }
            val player = remember {
                TablePlayer(
                    id = "overview-test-player", name = "TEST PLAYER",
                    battlefield = listOf(GameCard(1, "Test Creature", "Creature", Color(0xFF36584E)))
                )
            }
            Column {
                Button(onClick = { active = !active }) { Text("CHANGE ACTIVE PLAYER") }
                PlayerOverview(
                    player = player, index = 0, modifier = Modifier.fillMaxWidth(),
                    isActivePlayer = active, turnNumber = 1, phaseName = "MAIN",
                    turnTime = null, showTurnTimer = false, onToggleTimer = {},
                    onMoveUp = {}, onMoveDown = {}, onOpen = {}, onZone = {},
                    onPreview = {}, onPublicPreview = {}, canReorder = false,
                    tableZoom = zoom, onTableZoomEnd = {}
                )
            }
        }
        // Active player starts collapsed rather than expanding automatically.
        rule.onNodeWithContentDescription("Expand player details").assertExists()
        rule.onNodeWithTag("table-card-zoom").assertDoesNotExist()
        rule.onNodeWithContentDescription("Expand player details").performClick()
        rule.onNodeWithTag("table-card-zoom").assertExists()

        // Switching whose turn it is never overrides the player's manual selection.
        rule.onNodeWithText("CHANGE ACTIVE PLAYER").performClick()
        rule.onNodeWithContentDescription("Collapse player details").assertExists()
        rule.onNodeWithText("CHANGE ACTIVE PLAYER").performClick()
        rule.onNodeWithContentDescription("Collapse player details").assertExists()

        rule.onNodeWithContentDescription("Collapse player details").performClick()
        rule.onNodeWithTag("table-card-zoom").assertDoesNotExist()
    }

    @Test fun tableZoomPreferencesDoNotChangeThePlayerBoardSetting() {
        assertNotEquals(boardZoomKey(411, 891), tableZoomKey(411, 891))
        assertNotEquals(tableZoomKey(411, 891), tableZoomKey(891, 411))
        assertNotEquals(tableZoomKey(344, 882), tableZoomKey(673, 841))
    }
}
