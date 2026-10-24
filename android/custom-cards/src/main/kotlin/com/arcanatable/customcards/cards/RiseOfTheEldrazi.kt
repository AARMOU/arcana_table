package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val RiseOfTheEldrazi = card("Rise of the Eldrazi") {
    manaCost = "{9}{C}{C}{C}"
    colorIdentity = ""
    typeLine = "Sorcery"
    cantBeCountered = true
    oracleText = "This spell can't be countered.\nDestroy target permanent.\n" +
        "Target player draws four cards.\nTake an extra turn after this one.\nExile Rise of the Eldrazi."

    spell {
        selfExile()
        val permanent = target(TargetFilter.Permanent)
        val player = target(Targets.Player)
        effect = Effects.Destroy(permanent) then
            Effects.DrawCards(4, player) then
            Effects.TakeExtraTurn(EffectTarget.PlayerRef(Player.You))
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "716"
        artist = "Eli Minaya"
    }
}
