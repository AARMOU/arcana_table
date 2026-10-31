package com.arcanatable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/*
 * The 2–4 player lobby for Wi-Fi and online Commander. The host sees every seat, can change open
 * seats and the table size, remove a player, and starts the game once everyone is ready. Guests
 * see the same roster, mark themselves ready, and can leave. The roster comes from
 * ArgentumWaitingLobby.lobbyMessage(): names, commanders and readiness, never decks.
 */

private val Danger = Color(0xFFE9978B)

/** One seat of the lobby roster, as sent by the host. */
internal data class LobbySeat(
    val seatId: String,
    val kind: String, // host | human | waiting | ai
    val name: String,
    val commander: String?,
    val ready: Boolean,
)

internal fun lobbySeats(state: JSONObject?): List<LobbySeat> {
    val seats = state?.optJSONArray("seats") ?: return emptyList()
    return (0 until seats.length()).mapNotNull { seats.optJSONObject(it) }.map { seat ->
        LobbySeat(
            seatId = seat.optString("seatId"),
            kind = seat.optString("kind"),
            name = seat.optString("name"),
            commander = seat.optString("commander").takeUnless { it.isBlank() || it == "null" },
            ready = seat.optBoolean("ready"),
        )
    }
}

/** The line under the roster that says what the table is waiting for. */
internal fun lobbyStatusLine(state: JSONObject?, isHost: Boolean): String {
    if (state == null) return if (isHost) "Opening the room…" else "Connected. Waiting for the host…"
    val open = state.optInt("openSeats")
    val notReady = state.optJSONArray("notReady") ?: JSONArray()
    val names = (0 until notReady.length()).map { notReady.optString(it) }
    return when {
        open > 0 -> "Waiting for $open more ${if (open == 1) "friend" else "friends"} to join."
        names.isNotEmpty() -> "Waiting for ${names.joinToString()} to ready up."
        isHost -> "Everyone is ready. Start the game when you're set."
        else -> "Everyone is ready. Waiting for the host to start."
    }
}

/** Shows the multiplayer lobby when this phone is hosting or waiting in a 2–4 player room. */
@Composable
internal fun TableController.MultiplayerLobbyScreen() {
    // Read observable state FIRST. wifiMultiLobby and wifiServer are plain fields;
    // checking either before this state short-circuits Compose's initial read,
    // so publishing a ready host room never recomposes the first lobby screen.
    val hostLobbyState = wifiMultiLobbyState
    val hosting = hostLobbyState != null && wifiMultiLobby != null &&
        (wifiServer is ArgentumLanMultiServer || wifiServer is ArgentumCloudMultiHost)
    val guesting = wifiGuestWaiting && wifiRole in setOf("guest", "online-guest")
    if (!hosting && !guesting) return
    val online = if (hosting) wifiServer is ArgentumCloudMultiHost else wifiRole == "online-guest"
    // A room being ready is not evidence this full-screen lobby reached the device.
    LaunchedEffect(hosting, online) {
        recordDiagnostic(
            "Multiplayer lobby screen displayed: ${if (online) "internet" else "Wi-Fi"}; ${if (hosting) "host" else "guest"}",
            DiagnosticCategory.CONNECTION,
        )
    }
    val state = if (hosting) hostLobbyState else wifiGuestLobbyState
    val seats = lobbySeats(state)
    val context = LocalContext.current
    var removeTarget by remember { mutableStateOf<LobbySeat?>(null) }
    var confirmClose by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
                // Header
                Text(if (online) "ONLINE COMMANDER LOBBY" else "WI-FI COMMANDER LOBBY", color = SoftWhite,
                    fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                val readyCount = seats.count { it.ready }
                Text(if (seats.isEmpty()) "Players" else "${seats.size} players · $readyCount of ${seats.size} ready",
                    color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))

                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // How others join
                    when {
                        hosting && online -> RoomCodeCard(onlineRoomCode, context)
                        hosting -> WifiAddressCard(wifiWaitingAddress, context)
                        online -> onlineCodeInput.takeIf { it.isNotBlank() }?.let {
                            Text("ROOM CODE · $it", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Experimental host approval only. Gameplay/Control still require separately approved deployments.
                    if (BuildConfig.DEBUG && hosting && online) {
                        val empty = seats.indexOfFirst { it.kind == "waiting" }
                        if (empty > 0) OutlinedButton(onClick = {
                            val host = wifiServer as? ArgentumCloudMultiHost
                            if (host?.inviteGoose(empty) { capability, error -> scope.launch {
                                if (wifiServer !== host) return@launch
                                if (capability == null) message = error ?: "Invitation unavailable."
                                else {
                                    val url = "https://aarmou.github.io/arcana_table/control/#goose-invite=${host.code}.$capability"
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                }
                            } != true) message = "Wait for the room connection, then try again."
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("INVITE GOOSEGETA · TEST", color = Mint, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Table size (host only)
                    if (hosting && state != null) SeatCountPicker(
                        current = state.optInt("seatCount"),
                        allowed = state.optJSONArray("canResize").toIntSet(),
                        enabled = !multiLobbySeatChangePending,
                        onPick = { resizeMultiLobby(it) },
                    )

                    if (seats.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Mint, strokeWidth = 2.dp)
                        Text(lobbyStatusLine(null, hosting), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                    seats.forEachIndexed { index, seat ->
                        SeatCard(
                            number = index + 1,
                            seat = seat,
                            isYou = if (hosting) seat.kind == "host" else seat.seatId == wifiGuestSeatId,
                            hostControls = hosting && index > 0,
                            controlsEnabled = !multiLobbySeatChangePending,
                            onMakeAi = { setMultiSeatFriend(index, friend = false) },
                            onOpenForFriend = { setMultiSeatFriend(index, friend = true) },
                            onRemove = { removeTarget = seat },
                        )
                    }
                    if (multiLobbySeatChangePending) Text("Updating seats with the room service…", color = Muted, fontSize = 12.sp)
                }

                // Status and actions
                Text(lobbyStatusLine(state, hosting), color = if (state?.optBoolean("canStart") == true) Mint else CounterGold,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (hosting) {
                        OutlinedButton(
                            onClick = { if (seats.any { it.kind == "human" }) confirmClose = true else stopWifi() },
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        ) { Text("CLOSE ROOM", color = Danger, fontWeight = FontWeight.Bold, maxLines = 1) }
                        Button(
                            onClick = { if (online) startMultiOnlineGame() else startMultiWifiGame() },
                            enabled = state?.optBoolean("canStart") == true && !multiLobbySeatChangePending,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                        ) { Text("START GAME", fontWeight = FontWeight.Black, maxLines = 1) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                argentumSocket.close()
                                stopWifi()
                                argentumEnabled = false
                                homeOpen = true
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        ) { Text("LEAVE LOBBY", color = Danger, fontWeight = FontWeight.Bold, maxLines = 1) }
                        val me = seats.firstOrNull { it.seatId == wifiGuestSeatId }
                        val iAmReady = me?.ready == true
                        Button(
                            onClick = { setGuestLobbyReady(!iAmReady) },
                            enabled = me != null,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            colors = if (iAmReady) ButtonDefaults.buttonColors(containerColor = PanelLight, contentColor = SoftWhite)
                                else ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                        ) { Text(if (iAmReady) "CANCEL READY" else "READY UP", fontWeight = FontWeight.Black, maxLines = 1) }
                    }
                }
            }
        }
    }

    removeTarget?.let { seat ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove ${seat.name}?") },
            text = { Text("They leave the lobby and their seat opens for another friend.") },
            confirmButton = { TextButton(onClick = { removeMultiGuest(seat.seatId); removeTarget = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("REMOVE PLAYER", color = Danger, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { removeTarget = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("CANCEL") } },
            containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted,
        )
    }
    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text("Close this room?") },
        text = { Text("Everyone in the lobby is disconnected and the room code stops working.") },
        confirmButton = { TextButton(onClick = { confirmClose = false; stopWifi() }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("CLOSE ROOM", color = Danger, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { confirmClose = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("KEEP OPEN") } },
        containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted,
    )
}

private fun JSONArray?.toIntSet(): Set<Int> =
    if (this == null) emptySet() else (0 until length()).map { optInt(it) }.toSet()

@Composable
private fun RoomCodeCard(code: String?, context: android.content.Context) {
    Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("ROOM CODE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            if (code == null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Mint, strokeWidth = 2.dp)
                Text("Opening a secure online room…", color = Mint, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
            } else {
                Text(code, color = Mint, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp,
                    modifier = Modifier.padding(top = 2.dp).semantics { contentDescription = "Room code ${code.toList().joinToString(" ")}" })
                Text("Friends open Play, choose Join 2–4 players online, and enter this code.", color = Muted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { copyText(context, "Arcana Table room code", code) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text("COPY CODE", color = Mint, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    OutlinedButton(onClick = {
                        shareText(context, "Join my Arcana Table Commander game! Open Play, choose Join 2–4 players online, and enter code $code")
                    }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text("SHARE INVITE", color = Mint, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiAddressCard(address: String?, context: android.content.Context) {
    Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("SAME WI-FI", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Friends open Play, choose Join on Wi-Fi, and pick this phone. If it isn't listed, they type this address:",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text(address ?: "—", color = Mint, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                if (address != null) OutlinedButton(onClick = { copyText(context, "Arcana Table host address", address) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("COPY ADDRESS", color = Mint, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SeatCountPicker(current: Int, allowed: Set<Int>, enabled: Boolean, onPick: (Int) -> Unit) {
    Column {
        Text("PLAYERS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..4).forEach { count ->
                val selected = count == current
                val canPick = enabled && (selected || count in allowed)
                OutlinedButton(
                    onClick = { if (!selected) onPick(count) },
                    enabled = canPick,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = if (selected) ButtonDefaults.outlinedButtonColors(containerColor = Mint, contentColor = Ink)
                        else ButtonDefaults.outlinedButtonColors(contentColor = SoftWhite),
                    border = BorderStroke(1.dp, if (selected) Mint else PanelLight),
                ) { Text("$count", fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
private fun SeatCard(
    number: Int,
    seat: LobbySeat,
    isYou: Boolean,
    hostControls: Boolean,
    controlsEnabled: Boolean,
    onMakeAi: () -> Unit,
    onOpenForFriend: () -> Unit,
    onRemove: () -> Unit,
) {
    val waiting = seat.kind == "waiting"
    Surface(
        Modifier.fillMaxWidth().border(if (isYou) 2.dp else 0.dp, if (isYou) Mint else Color.Transparent, RoundedCornerShape(14.dp)),
        color = Panel, shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 84.dp, height = 60.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight),
                    contentAlignment = Alignment.Center) {
                    val commander = seat.commander
                    if (commander != null) CardArt(homeArtUrl(null, null, commander), Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop, fallbackName = commander, label = "$commander, commander of ${seat.name}")
                    else Text(if (waiting) "?" else "P$number", color = Muted, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(seat.name, color = if (waiting) Muted else SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val role = listOfNotNull(
                        "P$number",
                        when (seat.kind) { "host" -> "HOST"; "ai" -> "AI"; "human" -> "FRIEND"; else -> "OPEN SEAT" },
                        "YOU".takeIf { isYou },
                    ).joinToString(" · ")
                    Text(role, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    seat.commander?.let {
                        Text(it, color = CounterGold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                StatusChip(seat)
            }
            if (hostControls) Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                when (seat.kind) {
                    "waiting" -> TextButton(onClick = onMakeAi, enabled = controlsEnabled, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("PLAY AS AI", color = if (controlsEnabled) Mint else Muted, fontWeight = FontWeight.Bold) }
                    "ai" -> TextButton(onClick = onOpenForFriend, enabled = controlsEnabled, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("OPEN FOR A FRIEND", color = if (controlsEnabled) Mint else Muted, fontWeight = FontWeight.Bold) }
                    "human" -> TextButton(onClick = onRemove, enabled = controlsEnabled, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("REMOVE", color = if (controlsEnabled) Danger else Muted, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(seat: LobbySeat) {
    val (label, color) = when {
        seat.kind == "waiting" -> "WAITING" to Muted
        seat.ready -> "READY" to Mint
        else -> "NOT READY" to CounterGold
    }
    Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp,
        modifier = Modifier.widthIn(min = 72.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight)
            .padding(horizontal = 10.dp, vertical = 8.dp))
}

private fun copyText(context: android.content.Context, label: String, text: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
}

private fun shareText(context: android.content.Context, text: String) {
    runCatching {
        context.startActivity(android.content.Intent.createChooser(
            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }, "Share room invite"
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
