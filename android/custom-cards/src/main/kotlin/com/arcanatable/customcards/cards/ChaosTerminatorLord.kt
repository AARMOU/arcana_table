package com.arcanatable.customcards.cards
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
val ChaosTerminatorLord = card("Chaos Terminator Lord") {
    manaCost = "{3}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Astartes Warrior"
    power = 3
    toughness = 3
    oracleText = "Lord of Chaos — At the beginning of combat on your turn, another target creature you control gains double strike until end of turn."
    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.BEGIN_COMBAT)
        val creature = target(TargetFilter.OtherCreatureYouControl)
        effect = Effects.GrantKeyword(Keyword.DOUBLE_STRIKE, creature)
    }
    metadata { rarity = Rarity.RARE; artist = "Games Workshop" }
}
