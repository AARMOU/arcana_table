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

/**
 * Sakashima of a Thousand Faces. When it copies something it keeps "the legend rule doesn't apply to
 * permanents you control" through a trigger as it enters; so that a copy of a legend you control
 * isn't put into the graveyard before that trigger resolves, the copy is made nonlegendary. Partner
 * only matters for deck building.
 */
val SakashimaOfAThousandFaces = card("Sakashima of a Thousand Faces") {
    manaCost = "{3}{U}"
    colorIdentity = "U"
    typeLine = "Legendary Creature — Human Rogue"
    power = 3
    toughness = 1
    oracleText = "You may have Sakashima enter as a copy of another creature you control, except it has Sakashima's " +
        "other abilities.\nThe \"legend rule\" doesn't apply to permanents you control.\nPartner (You can have two " +
        "commanders if both have partner.)"

    val legendRule = LegendRuleDoesNotApplyTo(GameObjectFilter.Permanent.youControl())

    replacementEffect(
        EntersAsCopy(
            optional = true,
            copyFilter = GameObjectFilter.Creature.youControl(),
            exceptions = CopyExceptions(
                removedSupertypes = setOf(Supertype.LEGENDARY),
                addedTriggeredAbilities = listOf(
                    TriggeredAbility.create(
                        trigger = Triggers.self.enters(),
                        effect = Effects.GrantStaticAbility(legendRule, EffectTarget.Self, Duration.WhileSourceOnBattlefield()),
                        descriptionOverride = "The \"legend rule\" doesn't apply to permanents you control.",
                    ),
                ),
            ),
        ),
    )

    staticAbility { ability = legendRule }
    keywordAbility(KeywordAbility.partner())

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "84"
        artist = "Kieran Yanner"
    }
}
