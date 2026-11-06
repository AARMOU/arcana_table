package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.predicates.ControllerPredicate
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Goblin Welder (Urza's Legacy #80).
 *
 * - "target artifact card in **that player's** graveyard": the second target is owned by the
 *   controller of the first (`OwnedByReferencedPlayer(TargetController)`). Engine patch 0044 makes
 *   the engine treat that as a dependent target, so the graveyard choices are offered per artifact
 *   chosen (and the ability is only offered when some legal pair exists).
 * - "If both targets are still legal": both handles must still resolve; a target that became
 *   illegal (CR 608.2b) leaves its handle empty, so the gate fails and nothing happens (ruling
 *   2008-08-01).
 * - "that player simultaneously sacrifices … and returns …": the artifact's controller sacrifices
 *   it and the card returns under its owner's — the same player's — control, in one resolution
 *   with no priority or trigger processing in between.
 */
val GoblinWelder = card("Goblin Welder") {
    manaCost = "{R}"
    colorIdentity = "R"
    typeLine = "Creature — Goblin Artificer"
    power = 1
    toughness = 1
    oracleText = "{T}: Choose target artifact a player controls and target artifact card in that player's graveyard. " +
        "If both targets are still legal as this ability resolves, that player simultaneously sacrifices the artifact " +
        "and returns the artifact card to the battlefield."

    activatedAbility {
        cost = Costs.Tap
        val onField = target(TargetFilter(GameObjectFilter.Artifact))
        val inYard = target(
            TargetFilter(
                GameObjectFilter.Artifact.copy(
                    controllerPredicate = ControllerPredicate.OwnedByReferencedPlayer(EffectTarget.TargetController)
                ),
                zone = Zone.GRAVEYARD,
            )
        )
        effect = Effects.If(
            Conditions.All(
                Conditions.EntityMatches(onField, GameObjectFilter.Artifact),
                Conditions.EntityMatches(inYard, GameObjectFilter.Artifact),
            ),
            Effects.SacrificeTarget(onField, sacrificedByItsController = true) then
                Effects.PutOntoBattlefieldFromGraveyard(inYard),
        )
        description = "{T}: Choose target artifact a player controls and target artifact card in that player's graveyard. " +
            "If both targets are still legal as this ability resolves, that player simultaneously sacrifices the artifact " +
            "and returns the artifact card to the battlefield."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "80"
        artist = "Scott M. Fischer"
        imageUri = "https://cards.scryfall.io/normal/front/1/7/17cd920a-de09-454a-a9da-c84512e3aff1.jpg"
    }
}
