package com.arcanatable.customcards.support

import com.arcanatable.customcards.exportLine
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.tooling.CardLoader

/** A real engine game with one not-yet-registered custom definition round-tripped through Argentum JSON. */
fun customScenario(card: CardDefinition, vararg libraryCards: String): GameTestDriver =
    CardPool.driver(*libraryCards).apply {
        registerCards(listOf(CardLoader.fromJsonPreservingIds(exportLine(card))))
        initMirrorMatch(Deck.of("Plains" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
