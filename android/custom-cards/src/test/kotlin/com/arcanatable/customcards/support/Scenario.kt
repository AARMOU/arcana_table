package com.arcanatable.customcards.support

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId

/** A two-player game in player 1's first precombat main phase, decks of [filler]. */
fun scenario(vararg libraryCards: String, filler: String = "Plains"): GameTestDriver =
    CardPool.driver(*libraryCards).apply {
        initMirrorMatch(Deck.of(filler to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

/** Activates the [index]th activated ability printed on (or granted to) [permanent]'s card. */
fun GameTestDriver.activate(player: EntityId, permanent: EntityId, index: Int = 0, cardName: String? = null): ExecutionResult {
    val name = cardName ?: getCardName(permanent)!!
    val ability = cardRegistry.getCard(name)!!.script.activatedAbilities[index]
    return submit(ActivateAbility(player, permanent, ability.id))
}

fun GameTestDriver.manaPool(player: EntityId): ManaPoolComponent = state.getEntity(player)!!.get<ManaPoolComponent>()!!

fun GameTestDriver.permanentsNamed(player: EntityId, name: String): List<EntityId> = getPermanents(player).filter { getCardName(it) == name }

fun GameTestDriver.zoneNames(player: EntityId, zone: Zone): List<String> =
    state.getZone(player, zone).mapNotNull { state.getEntity(it)?.get<CardComponent>()?.name }

/** Passes priority until the stack is empty and no decision is pending (resolving everything). */
fun GameTestDriver.resolveStack(maxPasses: Int = 30) {
    repeat(maxPasses) {
        if (state.pendingDecision != null) autoResolveDecision()
        else if (state.stack.isEmpty()) return
        else bothPass()
    }
}

/** How a test answers the engine's questions while resolving. */
data class Choices(
    val yes: Boolean = true,
    val pick: List<String> = emptyList(),
    val color: com.wingedsheep.sdk.core.Color = com.wingedsheep.sdk.core.Color.RED,
    val targets: List<EntityId> = emptyList(),
)

/** Answers the one pending decision according to [c]. */
fun GameTestDriver.decide(c: Choices = Choices()) {
    val decision = state.pendingDecision ?: return
    val who = decision.playerId
    fun nameOf(id: EntityId) = getCardName(id)
    when (decision) {
        is com.wingedsheep.engine.core.YesNoDecision -> submitYesNo(who, c.yes)
        is com.wingedsheep.engine.core.SelectCardsDecision -> {
            val wanted = decision.options.filter { nameOf(it) in c.pick }.take(decision.maxSelections)
            submitCardSelection(who, wanted.ifEmpty { decision.options.take(decision.minSelections) })
        }
        is com.wingedsheep.engine.core.SearchLibraryDecision -> {
            val wanted = decision.options.filter { decision.cards[it]?.name in c.pick || nameOf(it) in c.pick }.take(decision.maxSelections)
            submitDecision(who, com.wingedsheep.engine.core.CardsSelectedResponse(decision.id, wanted.ifEmpty { decision.options.take(decision.minSelections) }))
        }
        is com.wingedsheep.engine.core.ChooseTargetsDecision -> {
            val chosen = decision.targetRequirements.associate { req ->
                val legal = decision.legalTargets[req.index].orEmpty()
                val preferred = legal.filter { it in c.targets }
                req.index to preferred.ifEmpty { legal.take(req.minTargets) }
            }
            submitDecision(who, com.wingedsheep.engine.core.TargetsResponse(decision.id, chosen))
        }
        is com.wingedsheep.engine.core.ChooseColorDecision ->
            submitDecision(who, com.wingedsheep.engine.core.ColorChosenResponse(decision.id, c.color.takeIf { it in decision.availableColors } ?: decision.availableColors.first()))
        is com.wingedsheep.engine.core.ChooseOptionDecision ->
            submitDecision(who, com.wingedsheep.engine.core.OptionChosenResponse(decision.id, 0))
        else -> autoResolveDecision()
    }
}

/** Resolves the whole stack, answering every question according to [c]. */
fun GameTestDriver.resolveAll(c: Choices = Choices(), maxSteps: Int = 60) {
    repeat(maxSteps) {
        when {
            state.pendingDecision != null -> decide(c)
            state.stack.isNotEmpty() -> bothPass()
            else -> return
        }
    }
    error("Stack did not settle: ${getStackSpellNames()} decision=${state.pendingDecision}")
}

/** Marks a permanent the driver put onto the battlefield as a token. */
fun GameTestDriver.putToken(player: EntityId, name: String): EntityId {
    val id = putPermanentOnBattlefield(player, name)
    replaceState(state.updateEntity(id) { it.with(com.wingedsheep.engine.state.components.identity.TokenComponent) })
    return id
}

/** The legal "activate an ability of [source]" actions for [player]. */
fun GameTestDriver.abilityActions(player: EntityId, source: EntityId): List<com.wingedsheep.engine.legalactions.LegalAction> =
    legalActions(player).filter { (it.action as? ActivateAbility)?.sourceId == source }

val GameTestDriver.projected get() = state.projectedState
