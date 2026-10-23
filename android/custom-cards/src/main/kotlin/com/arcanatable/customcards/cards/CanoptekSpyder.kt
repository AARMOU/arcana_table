package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

/**
 * Triggers for a nontoken artifact creature or a nontoken Vehicle, including a
 * noncreature Vehicle. Does not trigger for itself or for opponent permanents.
 */
val CanoptekSpyder = card("Canoptek Spyder") {
    manaCost = "{5}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Spider"
    power = 4
    toughness = 4
    oracleText = "Flying\nFabricator Claw Array — Whenever another nontoken artifact creature " +
        "or Vehicle you control enters, draw a card."

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.another(
            (GameObjectFilter.ArtifactCreature or
                GameObjectFilter.Artifact.withSubtype("Vehicle")).youControl().nontoken()
        ).enters()
        effect = Effects.DrawCards(1)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "151"
        artist = "Wisnu Tan"
    }
}
