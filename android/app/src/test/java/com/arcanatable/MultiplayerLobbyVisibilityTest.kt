package com.arcanatable

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.arcanatable.engine.LocalSeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Build 136 regression: a newly created host reported "lobby opened" but never
 * logged "lobby screen displayed". Before this fix, the Composable checked a
 * plain, initially-null wifiMultiLobby before ever reading the observable
 * wifiMultiLobbyState. No observable state was subscribed to for host creation.
 *
 * Runs the ACTUAL full-screen lobby Composable, not merely the Play menu.
 * Does not start networking, a rules engine, or a Cloudflare connection.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class MultiplayerLobbyVisibilityTest {
    @get:Rule val rule = createComposeRule()

    private fun seat(name: String) = LocalSeat(name, List(40) { "Forest" }, null)

    private fun room() = ArgentumWaitingLobby(
        seat("Host"),
        listOf(seat("AI 2"), seat("AI 3"), seat("AI 4")),
        4,
    ).also {
        it.setKind(1, ArgentumWaitingLobby.Kind.FRIEND)
        it.setKind(2, ArgentumWaitingLobby.Kind.FRIEND)
        it.setKind(3, ArgentumWaitingLobby.Kind.FRIEND)
    }

    @Test fun wifiHostOpenedAfterInitialCompositionDisplaysAndClosesLobby() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val controller = TableController(context, scope, CardZoomController())
        val lobby = room()
        val server = ArgentumLanMultiServer(
            "Host", lobby.friendSeatIds(),
            onGuest = { _, _ -> },
            onGuestMessage = { _, _ -> },
            onGuestLeft = { _ -> },
        )
        try {
            rule.setContent { controller.MultiplayerLobbyScreen() }
            rule.onNodeWithText("WI-FI COMMANDER LOBBY").assertDoesNotExist()
            // Plain fields arrive before the last published mutableState snapshot,
            // matching the sequence observed in the user's Build 136 diagnostics.
            rule.runOnIdle {
                controller.wifiServer = server
                controller.wifiMultiLobby = lobby
                controller.wifiWaitingAddress = "192.0.2.1"
                controller.wifiMultiLobbyState = lobby.lobbyMessage()
            }
            rule.onNodeWithText("WI-FI COMMANDER LOBBY").assertIsDisplayed()
            rule.onNodeWithText("CLOSE ROOM").assertIsDisplayed()
            rule.onNodeWithText("START GAME").assertIsDisplayed()
            rule.runOnIdle { controller.stopWifi() }
            rule.onNodeWithText("WI-FI COMMANDER LOBBY").assertDoesNotExist()
        } finally {
            rule.runOnIdle { controller.stopWifi() }
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

    @Test fun onlineHostOpenedAfterInitialCompositionDisplaysLobbyWithoutNetwork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val controller = TableController(context, scope, CardZoomController())
        val lobby = room()
        val cloud = ArgentumCloudMultiHost(
            "https://invalid.example", "Host", listOf("friend", "friend", "friend"),
            onGuest = { _, _ -> },
            onGuestMessage = { _, _ -> },
            onGuestLeft = { _ -> },
            onFailed = { _ -> },
        )
        try {
            rule.setContent { controller.MultiplayerLobbyScreen() }
            rule.onNodeWithText("ONLINE COMMANDER LOBBY").assertDoesNotExist()
            rule.runOnIdle {
                controller.wifiServer = cloud
                controller.wifiMultiLobby = lobby
                controller.onlineRoomCode = "ABCDEF"
                controller.wifiMultiLobbyState = lobby.lobbyMessage()
            }
            rule.onNodeWithText("ONLINE COMMANDER LOBBY").assertIsDisplayed()
            rule.onNodeWithText("CLOSE ROOM").assertIsDisplayed()
            rule.runOnIdle { controller.stopWifi() }
            rule.onNodeWithText("ONLINE COMMANDER LOBBY").assertDoesNotExist()
        } finally {
            rule.runOnIdle { controller.stopWifi() }
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }
}
