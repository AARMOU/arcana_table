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

val CyberdriveAwakener = card("Cyberdrive Awakener") {
    manaCost = "{5}{U}"
    colorIdentity = "U"
    typeLine = "Artifact Creature — Construct"
    power = 4
    toughness = 4
    oracleText = "Flying\nOther artifact creatures you control have flying.\nWhen this creature enters, each " +
        "noncreature artifact you control becomes a 4/4 artifact creature until end of turn."

    keywords(Keyword.FLYING)

    staticAbility {
        ability = GrantKeyword(Keyword.FLYING, GroupFilter(GameObjectFilter.ArtifactCreature.youControl()).other())
    }

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Artifact.notCreature().youControl()),
            Effects.BecomeCreature(EffectTarget.IterationEntity, power = 4, toughness = 4, duration = Duration.EndOfTurn),
        )
        description = "When this creature enters, each noncreature artifact you control becomes a 4/4 artifact creature until end of turn."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "69"
        artist = "Zezhou Chen"
    }
}
