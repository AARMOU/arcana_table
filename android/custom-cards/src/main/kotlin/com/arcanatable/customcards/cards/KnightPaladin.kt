package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Knight Paladin — Warhammer 40,000 Commander #160. */
val KnightPaladin = card("Knight Paladin") {
    manaCost = "{5}"
    colorIdentity = ""
    typeLine = "Artifact — Vehicle"
    power = 6
    toughness = 6
    oracleText = "Trample\nRapid-fire Battle Cannon — When this Vehicle enters, it deals 4 damage to each opponent.\nCrew 1"

    keywords(Keyword.TRAMPLE)
    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.DealDamage(4, EffectTarget.PlayerRef(Player.EachOpponent))
        description = "When this Vehicle enters, it deals 4 damage to each opponent."
    }
    keywordAbility(KeywordAbility.crew(1))

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "160"
        artist = "Games Workshop"
    }
}
