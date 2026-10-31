package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

val SurgeConductor = card("Surge Conductor") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Robot"
    power = 3
    toughness = 2
    oracleText = "Whenever another nontoken artifact you control enters, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)"

    triggeredAbility {
        trigger = Triggers.another(GameObjectFilter.Artifact.youControl().nontoken()).enters()
        effect = Effects.Proliferate()
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "19"
        artist = "Alexandr Leskinen"
    }
}
