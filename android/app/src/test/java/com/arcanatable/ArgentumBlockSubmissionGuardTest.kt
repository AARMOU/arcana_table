package com.arcanatable

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replays the first accepted blocker action and later rejected duplicate from B136. */
class ArgentumBlockSubmissionGuardTest {
    @Test fun acceptedBlockersSuppressDuplicateUntilCombatAdvances() {
        val guard = ArgentumBlockSubmissionGuard()
        guard.onStep("DECLARE_BLOCKERS")
        assertFalse(guard.submitted)
        guard.markSent()
        guard.onStep("DECLARE_BLOCKERS")  // v75: same step, action already accepted
        assertTrue(guard.submitted)
        guard.onError("INVALID_ACTION", "Blockers have already been declared")
        assertTrue(guard.submitted)
        guard.onStep("COMBAT_DAMAGE")
        assertFalse(guard.submitted)
    }

    @Test fun invalidAssignmentAllowsCorrectionInSameBlockStep() {
        val guard = ArgentumBlockSubmissionGuard()
        guard.onStep("DECLARE_BLOCKERS")
        guard.markSent()
        guard.onError("INVALID_ACTION", "Creature with flying cannot be blocked by this creature")
        assertFalse(guard.submitted)
        guard.markSent()
        guard.onStep("DECLARE_BLOCKERS")
        assertTrue(guard.submitted)
    }

    @Test fun secondCombatThisTurnAndNewGameCanDeclareAgain() {
        val guard = ArgentumBlockSubmissionGuard()
        guard.onStep("DECLARE_BLOCKERS")
        guard.markSent()
        guard.onStep("END_COMBAT")
        guard.onStep("DECLARE_BLOCKERS")
        assertFalse(guard.submitted)
        guard.markSent()
        guard.reset()
        guard.onStep("DECLARE_BLOCKERS")
        assertFalse(guard.submitted)
    }

    @Test fun unrelatedConnectionErrorsDoNotClearSubmittedDeclaration() {
        val guard = ArgentumBlockSubmissionGuard()
        guard.onStep("DECLARE_BLOCKERS")
        guard.markSent()
        guard.onError("WIFI", "Connection interrupted")
        assertTrue(guard.submitted)
    }
}
