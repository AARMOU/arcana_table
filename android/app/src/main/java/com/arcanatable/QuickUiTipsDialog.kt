package com.arcanatable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A session dismissal is separate from the persisted opt-out. */
@Composable
internal fun QuickUiTipsDialog(onDismiss: () -> Unit, onDontRemind: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag("quick-ui-tips"),
        onDismissRequest = onDismiss,
        title = { Text("Arcana Table — Quick UI Tips") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tap", fontWeight = FontWeight.Bold)
                Text("• Tap a card to see its available actions and abilities.\n• Tap an opponent’s player panel to expand and inspect their battlefield.\n• Tap the game log to review previous actions and events.\n• Tap displayed cards or notifications to dismiss them.")
                Text("Press & Hold", fontWeight = FontWeight.Bold)
                Text("• Press and hold Pass Turn to automatically pass priority until your next turn.\n• Long-press a card to inspect it more closely.")
                Text("Drag & Zoom", fontWeight = FontWeight.Bold)
                Text("• Pinch to zoom on the table to adjust your view.\n• Drag the hand drawer up or down to resize your hand.")
                Text("Customization", fontWeight = FontWeight.Bold)
                Text("Customize your interface through Options → Adjust UI, including card sizes, hand layout, player panels, status displays, and other interface elements.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } },
        dismissButton = { TextButton(onClick = onDontRemind) { Text("Don’t remind me again") } },
    )
}
