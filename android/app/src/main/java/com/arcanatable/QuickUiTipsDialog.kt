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
    StudioAlertDialog("Quick Ui Tips Dialog 1",
        modifier = Modifier.testTag("quick-ui-tips"),
        onDismissRequest = onDismiss,
        title = { StudioText("QuickUiTipsDialog.Text.1", "Dialogs and overlays", false, "Welcome to Arcana Table") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioText("QuickUiTipsDialog.Text.2", "Dialogs and overlays", false, "Learn the controls and Commander rules in short offline lessons.")
                StudioText("QuickUiTipsDialog.Text.3", "Dialogs and overlays", false, "Start with Your first turn, browse 20 topics, or look up a term in the glossary. The lessons do not change your saved games.")
                StudioText("QuickUiTipsDialog.Text.4", "Dialogs and overlays", false, "You can reopen this guide from Settings → Help & diagnostics or the in-game menu.")
            }
        },
        confirmButton = {
            StudioTextButton("QuickUiTipsDialog.TextButton.1", "Dialogs and overlays", onClick = onOpenTutorials, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("QuickUiTipsDialog.Text.5", "Dialogs and overlays", false, "OPEN TUTORIALS") }
        },
        dismissButton = {
            StudioTextButton("QuickUiTipsDialog.TextButton.2", "Dialogs and overlays", onClick = onDontRemind, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("QuickUiTipsDialog.Text.6", "Dialogs and overlays", false, "NOT NOW") }
        }
    )
}
