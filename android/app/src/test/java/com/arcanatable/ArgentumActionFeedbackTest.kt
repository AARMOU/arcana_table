package com.arcanatable

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentumActionFeedbackTest {
    @Test fun unaffordableOfferIsNotCalledAnEngineRejection() {
        val line = blockedArgentumActionLogLine(
            "CastSpell", BlockedArgentumActionReason.MARKED_UNAFFORDABLE)
        assertTrue(line.contains("cast spell"))
        assertTrue(line.contains("marked the offered option unaffordable"))
        assertTrue(line.contains("No action was sent"))
        assertFalse(line.contains("rejected"))
    }

    @Test fun unsupportedInputsAdmitThatEngineDidNotRejectTheAction() {
        val line = blockedArgentumActionLogLine(
            "ActivateAbility", BlockedArgentumActionReason.CLIENT_INPUT_UNSUPPORTED)
        assertTrue(line.contains("cannot collect all inputs"))
        assertTrue(line.contains("engine has not rejected"))
    }

    @Test fun missingEpochAndFailedSubmissionHaveDifferentKnownReasons() {
        val noEpoch = blockedArgentumActionLogLine(
            "PlayLand", BlockedArgentumActionReason.MISSING_INTERACTION_EPOCH)
        val notConfirmed = blockedArgentumActionLogLine(
            "PlayLand", BlockedArgentumActionReason.SUBMISSION_NOT_CONFIRMED)
        assertTrue(noEpoch.contains("interaction version was missing"))
        assertTrue(notConfirmed.contains("cause is unconfirmed"))
    }

    @Test fun unknownTypeNeverEchoesHiddenProtocolData() {
        val line = blockedArgentumActionLogLine(
            "private-card-id=secret-987", BlockedArgentumActionReason.SUBMISSION_NOT_CONFIRMED)
        assertTrue(line.contains("selected action"))
        assertFalse(line.contains("secret-987"))
    }
}
