package com.arcanatable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** First-launch invitation; tutorial content is always available again in Help and the game menu. */
@Composable
internal fun QuickUiTipsDialog(
    onDismiss: () -> Unit,
    onDontRemind: () -> Unit,
    onOpenTutorials: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("quick-ui-tips"),
        onDismissRequest = onDismiss,
        title = { Text("Welcome to Arcana Table") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Learn the controls and Commander rules in short offline lessons.")
                Text("Start with Your first turn, browse 20 topics, or look up a term in the glossary. The lessons do not change your saved games.")
                Text("You can reopen this guide from Settings → Help & diagnostics or the in-game menu.")
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenTutorials, modifier = Modifier.heightIn(min = 48.dp)) { Text("OPEN TUTORIALS") }
        },
        dismissButton = {
            TextButton(onClick = onDontRemind, modifier = Modifier.heightIn(min = 48.dp)) { Text("NOT NOW") }
        }
    )
}
