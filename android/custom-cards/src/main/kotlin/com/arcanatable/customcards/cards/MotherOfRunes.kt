package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.*
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.values.*

val MotherOfRunes = card("Mother of Runes") {
    manaCost = "{W}"
    colorIdentity = "W"
    typeLine = "Creature — Human Cleric"
    power = 1
    toughness = 1
    oracleText = "{T}: Target creature you control gains protection from the color of your choice until end of turn."

    activatedAbility {
        cost = Costs.Tap
        val creature = target(TargetFilter.CreatureYouControl)
        effect = Effects.ChooseColorThen(Effects.GrantProtectionFromChosenColor(creature))
        description = "{T}: Target creature you control gains protection from the color of your choice until end of turn."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "16"
        artist = "Scott M. Fischer"
    }
}
