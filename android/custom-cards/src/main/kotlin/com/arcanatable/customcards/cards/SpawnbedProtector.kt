package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/**
 * CARDKIT-SCAFFOLD — unregistered until optional-target engine scenarios pass.
 * Spawnbed Protector — M3C #36. The graveyard target is optional, but the two Scions
 * are created on every resolution, including when no eligible Eldrazi card is chosen.
 * The shared Scion factory gives each token its own sacrifice-for-{C} mana ability.
 */
val SpawnbedProtector = card("Spawnbed Protector") {
    manaCost = "{7}"
    colorIdentity = ""
    typeLine = "Creature — Eldrazi"
    power = 6
    toughness = 8
    oracleText = "At the beginning of your end step, return up to one target Eldrazi creature card " +
        "from your graveyard to your hand. Create two 1/1 colorless Eldrazi Scion creature tokens " +
        "with \"Sacrifice this token: Add {C}.\""

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.END)
        val eldraziInOwnGraveyard = target(
            TargetFilter(
                GameObjectFilter.Creature.withSubtype("Eldrazi").ownedByYou(),
                zone = Zone.GRAVEYARD,
            ),
            optional = true,
        )
        effect = Effects.Move(
            eldraziInOwnGraveyard,
            Zone.HAND,
            fromZone = Zone.GRAVEYARD,
        ) then createEldraziScions(2)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "36"
        artist = "Maxime Minard"
    }
}
