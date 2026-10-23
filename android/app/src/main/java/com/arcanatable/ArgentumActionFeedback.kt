package com.arcanatable

/**
 * A local, viewer-facing explanation of why Arcana did NOT submit a selected
 * Argentum action. These are client observations, never engine rejection codes.
 *
 * The log intentionally excludes card names, IDs, targets and raw protocol
 * details, which could reveal hidden information if a game log is shared.
 */
internal enum class BlockedArgentumActionReason {
    MARKED_UNAFFORDABLE,
    CLIENT_INPUT_UNSUPPORTED,
    MISSING_INTERACTION_EPOCH,
    SUBMISSION_NOT_CONFIRMED,
}

internal fun blockedArgentumActionLogLine(
    actionType: String,
    reason: BlockedArgentumActionReason,
): String {
    val label = when (actionType) {
        "CastSpell" -> "cast spell"
        "PlayLand" -> "play land"
        "ActivateAbility" -> "activate ability"
        "DeclareAttackers" -> "declare attackers"
        "DeclareBlockers" -> "declare blockers"
        "PassPriority" -> "pass priority"
        else -> "selected action"
    }
    val explanation = when (reason) {
        BlockedArgentumActionReason.MARKED_UNAFFORDABLE ->
            "Argentum marked the offered option unaffordable. No action was sent."
        BlockedArgentumActionReason.CLIENT_INPUT_UNSUPPORTED ->
            "Arcana cannot collect all inputs this option requires. No action was sent; the engine has not rejected it."
        BlockedArgentumActionReason.MISSING_INTERACTION_EPOCH ->
            "The current interaction version was missing. No action was sent; refresh the game state."
        BlockedArgentumActionReason.SUBMISSION_NOT_CONFIRMED ->
            "Arcana could not submit the action. The cause is unconfirmed; refresh or check the connection."
    }
    return "Local action — $label: $explanation"
}

internal fun TableController.logBlockedArgentumAction(
    actionType: String,
    reason: BlockedArgentumActionReason,
) {
    val line = blockedArgentumActionLogLine(actionType, reason)
    gameLog = (gameLog + line).takeLast(250)
    recordDiagnostic(line, DiagnosticCategory.ACTION)
}
