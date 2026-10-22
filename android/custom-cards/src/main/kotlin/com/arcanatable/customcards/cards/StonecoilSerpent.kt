package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersWithDynamicCounters
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.ProtectionScope

/** Uses genuine cast-X entry counters and the full targeting/damage/blocking protection quality. */
val StonecoilSerpent = card("Stonecoil Serpent") {
    manaCost = "{X}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Snake"
    power = 0
    toughness = 0
    oracleText = "Reach, trample, protection from multicolored\n" +
        "This creature enters with X +1/+1 counters on it."

    keywords(Keyword.REACH, Keyword.TRAMPLE)
    keywordAbility(KeywordAbility.Protection(ProtectionScope.Multicolored))
    replacementEffect(EntersWithDynamicCounters(count = DynamicAmounts.xValue()))

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "976"
        artist = "Mark Poole"
    }
}
