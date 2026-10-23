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

val PowerDepot = card("Power Depot") {
    colorIdentity = ""
    typeLine = "Artifact Land"
    oracleText = "This land enters tapped.\n{T}: Add {C}.\n{T}: Add one mana of any color. Spend this mana only to " +
        "cast artifact spells or activate abilities of artifacts.\nModular 1 (This land enters with a +1/+1 counter " +
        "on it. When it's put into a graveyard from the battlefield, you may put its +1/+1 counters on target artifact creature.)"

    replacementEffect(EntersTapped())

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddAnyColorMana(1, ManaRestriction.CardTypeSpellsOrAbilitiesOnly(CardType.ARTIFACT, allowSpells = true, allowAbilities = true))
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "{T}: Add one mana of any color. Spend this mana only to cast artifact spells or activate abilities of artifacts."
    }

    keywordAbility(KeywordAbility.modular(1))
    replacementEffect(EntersWithCounters(counterType = CounterType.PLUS_ONE_PLUS_ONE, count = 1, selfOnly = true))

    triggeredAbility {
        val permanent = target(TargetFilter(GameObjectFilter.ArtifactCreature))
        trigger = Triggers.self.dies()
        optional = true
        effect = Effects.AddDynamicCounters(CounterType.PLUS_ONE_PLUS_ONE, DynamicAmounts.lastKnownPlusOneCounters(), permanent)
        description = "When this land is put into a graveyard from the battlefield, you may put its +1/+1 counters on target artifact creature."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "250"
        imageUri = "https://cards.scryfall.io/normal/front/0/3/032eba04-0a63-4823-85e1-64861330a8d7.jpg?1783926795"
        artist = "Steven Belledin"
    }
}
