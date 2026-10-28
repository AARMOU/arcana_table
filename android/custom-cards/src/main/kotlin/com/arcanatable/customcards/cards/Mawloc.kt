package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val Mawloc = card("Mawloc") {
    manaCost = "{X}{R}{G}"
    colorIdentity = "RG"
    typeLine = "Creature — Tyranid"
    power = 2
    toughness = 2
    oracleText = "Ravenous (This creature enters with X +1/+1 counters on it. If X is 5 or more, draw a card when it enters.)\nTerror from the Deep — When this creature enters, it fights up to one target creature an opponent controls. If that creature would die this turn, exile it instead."

    ravenous()

    triggeredAbility {
        trigger = Triggers.self.enters()
        val foe = target(TargetFilter.CreatureOpponentControls, optional = true)
        effect = Effects.Fight(EffectTarget.Self, foe) then
            Effects.MarkExileOnDeath(foe, Duration.EndOfTurn)
        description = "When this creature enters, it fights up to one target creature an opponent controls. If that creature would die this turn, exile it instead."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "133"
        artist = "Mathias Kollros"
        imageUri = "https://cards.scryfall.io/normal/front/b/b/bbec97ff-bb84-4496-9e09-d4fc1570c516.jpg?1783920840"
    }
}
