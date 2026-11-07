package com.arcanatable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Signals from game windows to the editor shell while Studio is editing. A game dialog's back/outside dismissal is held
 * (so a stray tap never cancels a decision) and offered to the user as an explicit "Close this dialog" choice; the
 * in-dialog Studio button opens the screen menu. Classic-panel behaviour (panelOpen) is unchanged.
 */
internal object StudioEditorRequests {
    var pendingDismiss by mutableStateOf<(()->Unit)?>(null);private set
    var menuRequests by mutableIntStateOf(0);private set
    var handRequests by mutableIntStateOf(0);private set
    /** True from an entry point's request until the shell has opened the hand screen (the shell may not exist yet). */
    var handPending by mutableStateOf(false)
    fun openHand(){handRequests++;handPending=true}
    fun dismiss(close:()->Unit){pendingDismiss=close}
    fun openMenu(){menuRequests++}
    fun consumeDismiss():(()->Unit)?=pendingDismiss.also{pendingDismiss=null}
    fun clear(){pendingDismiss=null}
    // Editor settings shared with game dialog windows, which draw their own selection canvas.
    var shellActive by mutableStateOf(false)
    var tool by mutableStateOf(StudioEditorTool.Select)
    var snap by mutableStateOf(true)
    var outlines by mutableStateOf(true)
    /** The window the shell itself draws on; every other window (dialog, sheet) hosts its own canvas. */
    var mainWindow by mutableStateOf(0)
    /** One selection canvas per secondary window, owned by whichever Studio control in it claimed it first. */
    val canvasOwners=androidx.compose.runtime.mutableStateMapOf<Int,Any>()
    /** Game pop-ups currently drawn in place by the editor, oldest first. */
    val popups=androidx.compose.runtime.mutableStateListOf<StudioPopupEntry>()
}
