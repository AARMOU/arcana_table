package com.arcanatable

import android.content.Context
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** In-game lobby viewing must never reopen a waiting room or disrupt a live multiplayer match. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class ActiveMultiplayerRoomViewerTest {
    @get:Rule val rule = createComposeRule()

    @Test fun viewCurrentRoomAndReturnWithoutDisconnecting() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val controller = TableController(context, scope, CardZoomController())
        try {
            controller.wifiRole = "online-guest"
            controller.argentumEnabled = true
            controller.gameStartedOnline = true
            controller.activeLobbyViewerOpen = true
            controller.argentumSnapshot = ArgentumTableSnapshot(
                players = listOf(
                    TablePlayer("Host", commanderName = "Mabel, Heir to Cragflame"),
                    TablePlayer("Friend", commanderName = "Ayula, Queen Among Bears"),
                ),
                viewingPlayer = 1,
                activePlayer = 0,
                turnNumber = 3,
                phaseName = "MAIN",
                stepName = "",
                gameOver = false,
                legalActions = emptyList(),
                interactionEpoch = null,
                serverCardIds = emptyMap(),
                targets = emptyMap(),
            )
            rule.setContent { controller.ActiveMultiplayerRoomViewer() }
            rule.onNodeWithText("CURRENT ROOM").assertIsDisplayed()
            rule.onNodeWithText("Host").assertIsDisplayed()
            rule.onNodeWithText("Friend").assertIsDisplayed()
            rule.onNodeWithText("Ayula, Queen Among Bears").assertIsDisplayed()
            rule.onNodeWithText("BACK TO GAME").performClick()
            rule.onNodeWithText("CURRENT ROOM").assertDoesNotExist()
            rule.runOnIdle {
                assertFalse(controller.activeLobbyViewerOpen)
                assertTrue(controller.argentumEnabled)
                assertTrue(controller.gameStartedOnline)
                assertEquals("online-guest", controller.wifiRole)
            }
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

    @Test fun viewerNotShownOutsideAnActiveMultiplayerMatch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val controller = TableController(context, scope, CardZoomController())
        try {
            controller.activeLobbyViewerOpen = true
            controller.wifiRole = null
            controller.gameStartedOnline = true
            controller.argentumEnabled = true
            rule.setContent { controller.ActiveMultiplayerRoomViewer() }
            rule.onNodeWithText("CURRENT ROOM").assertDoesNotExist()
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }
}
