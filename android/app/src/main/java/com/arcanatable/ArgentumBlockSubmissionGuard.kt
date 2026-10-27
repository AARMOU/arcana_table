package com.arcanatable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Client-only guard for a successfully transmitted DeclareBlockers action.
 *
 * Argentum may offer DeclareBlockers again after accepting a declaration while
 * still in DECLARE_BLOCKERS (the B136 device trace shows this at v75/v114).
 * Do not invent any game action or mark an invalid declaration as accepted:
 * a server INVALID_ACTION releases the guard except when the response confirms
 * blockers were already declared. A new combat declaration after leaving the
 * step starts fresh, including multiple combat phases in one turn.
 */
internal class ArgentumBlockSubmissionGuard {
    var submitted by mutableStateOf(false)
        private set
    private var inBlockStep = false

    fun onStep(step: String) {
        val nowInBlockStep = step.equals("DECLARE_BLOCKERS", ignoreCase = true)
        if (!nowInBlockStep || !inBlockStep) submitted = false
        inBlockStep = nowInBlockStep
    }

    fun markSent() { submitted = true }

    fun onError(code: String, detail: String) {
        if (code != "INVALID_ACTION" || !submitted) return
        if (detail.contains("Blockers have already been declared", ignoreCase = true)) return
        // Illegal assignment (flying, menace, protection, etc.): allow a corrected
        // resubmission while the board still retains the choices. Argentum's blocker
        // validation messages all name blocking; an INVALID_ACTION for some other
        // action (a spell, an ability) must not reopen an accepted declaration.
        if (detail.contains("block", ignoreCase = true)) submitted = false
    }

    fun reset() {
        submitted = false
        inBlockStep = false
    }
}
