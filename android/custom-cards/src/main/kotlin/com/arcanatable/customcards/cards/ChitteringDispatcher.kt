package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity

/** Myriad copies are engine-managed, including their delayed leave events. */
val ChitteringDispatcher = card("Chittering Dispatcher") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Eldrazi Drone"
    power = 2
    toughness = 3
    oracleText = "Devoid\nMyriad\nWhen this creature leaves the battlefield, create a 0/1 colorless " +
        "Eldrazi Spawn creature token with \"Sacrifice this token: Add {C}.\""
    keywords(Keyword.DEVOID, Keyword.MYRIAD)
    triggeredAbility {
        trigger = Triggers.self.leaves()
        effect = Effects.CreateEldraziSpawn()
    }
    metadata { rarity = Rarity.RARE; collectorNumber = "63"; artist = "Jehan Choo" }
}
