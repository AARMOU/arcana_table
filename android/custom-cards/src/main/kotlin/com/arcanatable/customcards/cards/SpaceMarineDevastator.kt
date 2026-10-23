package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.dsl.mechanics.squad
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

val SpaceMarineDevastator = card("Space Marine Devastator") {
    manaCost = "{3}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Astartes Warrior"
    power = 3
    toughness = 3
    oracleText = "Squad {2} (As an additional cost to cast this spell, you may pay {2} any number of times. " +
        "When this creature enters, create that many tokens that are copies of it.)\n" +
        "Grav-cannon — When this creature enters, destroy up to one target artifact or enchantment."
    squad("{2}")

    triggeredAbility {
        trigger = Triggers.self.enters()
        val permanent = target(TargetFilter.ArtifactOrEnchantment, optional = true)
        effect = Effects.Destroy(permanent)
    }

    metadata { rarity = Rarity.RARE; collectorNumber = "14"; artist = "Games Workshop" }
}
