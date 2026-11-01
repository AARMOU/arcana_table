package com.arcanatable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Read-only view of the *current game* participants. The existing waiting lobby deliberately
 * releases its temporary readiness roster on START; reopening it mid-match would be wrong.
 *
 * Display only the authoritative, viewer-masked game projection. No new join, ready, room-close
 * or networking action is performed here, and dismissing the viewer cannot stop a live game.
 */
@Composable
internal fun TableController.ActiveMultiplayerRoomViewer() {
    if (!activeLobbyViewerOpen) return
    val role = wifiRole
    if (!argentumEnabled || !gameStartedOnline || gameEndedOnline ||
        role !in setOf("host", "online-host", "guest", "online-guest")) return

    val snapshot = argentumSnapshot
    val roster = snapshot?.players.orEmpty()
    val viewingPlayer = snapshot?.viewingPlayer ?: -1
    val online = role == "online-host" || role == "online-guest"

    Dialog(
        onDismissRequest = { activeLobbyViewerOpen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("CURRENT ROOM", color = SoftWhite, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text(
                    if (online) "Online Commander · active match" else "Wi-Fi Commander · active match",
                    color = Muted, fontSize = 13.sp,
                )
                Text("Players in your current game", color = SoftWhite, fontWeight = FontWeight.Bold)

                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (snapshot == null) {
                        Text("Waiting for the current match roster…", color = Muted)
                    } else {
                        roster.forEachIndexed { index, player ->
                            Surface(
                                Modifier.fillMaxWidth(),
                                color = Panel,
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            player.name,
                                            color = SoftWhite,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            if (index == viewingPlayer) "YOU" else "PLAYER ${index + 1}",
                                            color = if (index == viewingPlayer) Mint else Muted,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    player.commanderName?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, color = CounterGold, fontSize = 12.sp)
                                    }
                                    if (player.isEliminated) {
                                        Text("Eliminated", color = Muted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { activeLobbyViewerOpen = false },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                ) {
                    Text("BACK TO GAME", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
