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
    fun dismiss(close:()->Unit){pendingDismiss=close}
    fun openMenu(){menuRequests++}
    fun consumeDismiss():(()->Unit)?=pendingDismiss.also{pendingDismiss=null}
    fun clear(){pendingDismiss=null}
}
