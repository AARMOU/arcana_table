package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Triarch Stalker — Warhammer 40,000 Commander #67 (Necron Dynasties). {3}{B}{B} Artifact Creature —
 * Necron, 4/5.
 *
 * Targeting Relay — At the beginning of combat on your turn, choose an opponent.
 * Creatures attacking the last chosen player have menace.
 *
 * The trigger is `Triggers.you.beginningOf(Step.BEGIN_COMBAT)` running `Effects.ChooseOpponent()`, which
 * stores the opponent on the Stalker under its OPPONENT choice slot and **replaces** the previous one;
 * that is "the last chosen player", and it stays until the next pick (through the other players'
 * turns). Forced without a prompt when there is one opponent. Not targeted: chosen on resolution.
 *
 * The second line is a static `GrantKeyword(MENACE)` over every creature (any controller) whose
 * declared defender is that player themself — `attackingPlayer(PlayerRef(Player.ChosenOpponent))`,
 * which projects against the source's stored choice (engine patch 0037). An attacker aimed at a
 * planeswalker or battle the player controls is attacking that permanent, not the player, so it
 * gets nothing. Being a continuous effect, it also covers creatures put onto the battlefield attacking
 * that player, and menace is checked when blockers are declared (CR 509.1b, 702.111).
 *
 * The choice belongs to this object: when the Stalker leaves the battlefield the static ends, and a
 * Stalker that returns is a new object with no chosen player until its trigger resolves (CR 400.7).
 * A chosen player who has left the game can't be attacked, so the effect then does nothing.
 * No official rulings are published for this card (checked 2026-11-02).
 */
val TriarchStalker = card("Triarch Stalker") {
    manaCost = "{3}{B}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Necron"
    power = 4
    toughness = 5
    oracleText = "Targeting Relay — At the beginning of combat on your turn, choose an opponent.\nCreatures attacking the last chosen player have menace."

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.BEGIN_COMBAT)
        effect = Effects.ChooseOpponent("Targeting Relay — choose an opponent")
        description = "Targeting Relay — At the beginning of combat on your turn, choose an opponent."
    }

    staticAbility {
        ability = GrantKeyword(
            Keyword.MENACE,
            GroupFilter(GameObjectFilter.Creature.attackingPlayer(EffectTarget.PlayerRef(Player.ChosenOpponent))),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "67"
        artist = "JB Casacop"
        imageUri = "https://cards.scryfall.io/normal/front/f/b/fbe75aae-727b-4cb0-967c-477d5a08b8f7.jpg?1783920898"
    }
}
