package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val CompanyCommander = card("Company Commander") {
    manaCost = "{2}{W}{B}"
    colorIdentity = "WB"
    typeLine = "Creature — Human Soldier"
    power = 2
    toughness = 4
    oracleText = "Command Section — When this creature enters, create a number of 1/1 white Soldier creature tokens equal to the number of opponents you have.\n" +
        "Bring it Down! — Whenever this creature attacks, creatures you control gain deathtouch until end of turn."
    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.CreateToken(
            count = DynamicAmounts.playerCount(Player.EachOpponent),
            power = 1, toughness = 1, colors = setOf(Color.WHITE), creatureTypes = setOf("Soldier")
        )
    }
    triggeredAbility {
        trigger = Triggers.self.attacks()
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.youControl()),
            Effects.GrantKeyword(Keyword.DEATHTOUCH, EffectTarget.IterationEntity)
        )
    }
    metadata { rarity = Rarity.RARE; collectorNumber = "113"; artist = "Orjan Ruttenborg Svendsen" }
}
