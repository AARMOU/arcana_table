package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.WardCost
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Plague Drone — Warhammer 40,000 Commander #47. */
val PlagueDrone = card("Plague Drone") {
    manaCost = "{3}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Demon"
    power = 3
    toughness = 3
    oracleText = "Flying\nRot Fly — If an opponent would gain life, that player loses that much life instead."

    keywords(Keyword.FLYING)
    replacementEffect(ReplaceLifeGainWith(
        replacementEffect = Effects.LoseLife(
            DynamicAmounts.replacementLifeGainAmount(),
            EffectTarget.PlayerRef(Player.TriggeringPlayer)
        ),
        appliesTo = EventPattern.LifeGainEvent(Player.EachOpponent)
    ))

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "47"
        artist = "Games Workshop"
    }
}
