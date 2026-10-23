package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.WardCost

/** Cast-time X determines counters, ravenous draw, and ETB damage independently. */
val Zoanthrope = card("Zoanthrope") {
    manaCost = "{X}{U}{R}"
    colorIdentity = "UR"
    typeLine = "Creature — Tyranid"
    power = 0
    toughness = 0
    oracleText = "Ravenous\nFlying, ward {2}\nWarp Blast — When this creature enters, it deals X damage to any target."
    ravenous()
    keywords(Keyword.FLYING)
    keywordAbility(KeywordAbility.Ward(WardCost.Mana("{2}")))
    triggeredAbility {
        trigger = Triggers.self.enters()
        val victim = target(Targets.Any)
        effect = Effects.DealDamage(DynamicAmounts.castX(), victim)
    }
    metadata { rarity = Rarity.RARE; collectorNumber = "149"; artist = "Billy Christian" }
}
