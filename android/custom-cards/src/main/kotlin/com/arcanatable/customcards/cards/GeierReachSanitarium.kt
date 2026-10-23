package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.references.Player

/** Geier Reach Sanitarium — Commander Masters #1000 (Eldrazi Unbound). */
val GeierReachSanitarium = card("Geier Reach Sanitarium") {
    colorIdentity = ""
    typeLine = "Legendary Land"
    oracleText = "{T}: Add {C}.\n{2}, {T}: Each player draws a card, then discards a card."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{2}"), Costs.Tap)
        effect = Effects.ForEachPlayer(Player.ActivePlayerFirst, Effects.DrawCards(1)) then
            Patterns.Hand.eachPlayerDiscards(1)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "1000"
        artist = "Cliff Childs"
    }
}
